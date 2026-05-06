package com.guidelam.facto.auth;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.gmail.GmailScopes;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    public static final String REDIRECT_URI = "http://localhost:8080/oauth/callback";

    public static final List<String> SCOPES = List.of(
            GmailScopes.GMAIL_READONLY,
            DriveScopes.DRIVE_METADATA_READONLY,
            DriveScopes.DRIVE_FILE
    );

    private final SettingsService settings;
    private final TokenStore tokenStore;
    private final NetHttpTransport httpTransport;
    private final JsonFactory jsonFactory;

    public boolean credentialsConfigured() {
        return settings.get(AppSettingKeys.GOOGLE_CLIENT_ID).filter(s -> !s.isBlank()).isPresent()
                && settings.get(AppSettingKeys.GOOGLE_CLIENT_SECRET).filter(s -> !s.isBlank()).isPresent();
    }

    public Optional<GoogleAuthorizationCodeFlow> currentFlow() {
        var clientId = settings.get(AppSettingKeys.GOOGLE_CLIENT_ID).orElse(null);
        var clientSecret = settings.get(AppSettingKeys.GOOGLE_CLIENT_SECRET).orElse(null);
        if (clientId == null || clientSecret == null) {
            return Optional.empty();
        }
        return Optional.of(new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientId, clientSecret, SCOPES)
                .setAccessType("offline")
                .build());
    }

    public String buildAuthUrl() {
        var flow = currentFlow().orElseThrow(() ->
                new IllegalStateException("Identifiants Google non configurés"));
        return flow.newAuthorizationUrl()
                .setRedirectUri(REDIRECT_URI)
                .set("prompt", "consent")
                .build();
    }

    public void exchangeCodeAndStore(String code) throws IOException {
        var flow = currentFlow().orElseThrow(() ->
                new IllegalStateException("Identifiants Google non configurés"));
        TokenResponse response = flow.newTokenRequest(code)
                .setRedirectUri(REDIRECT_URI)
                .execute();
        tokenStore.saveFromResponse(response);
        log.info("Google OAuth tokens persisted (refresh token included: {})",
                response.getRefreshToken() != null);
    }
}
