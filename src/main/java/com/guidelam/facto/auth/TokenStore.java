package com.guidelam.facto.auth;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialRefreshListener;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenStore {

    private final SettingsService settings;
    private final NetHttpTransport httpTransport;
    private final JsonFactory jsonFactory;

    public boolean isAuthorized() {
        return settings.get(AppSettingKeys.GOOGLE_REFRESH_TOKEN)
                .filter(s -> !s.isBlank())
                .isPresent();
    }

    public void saveFromResponse(TokenResponse response) {
        if (response.getAccessToken() != null) {
            settings.set(AppSettingKeys.GOOGLE_ACCESS_TOKEN, response.getAccessToken());
        }
        if (response.getRefreshToken() != null) {
            settings.set(AppSettingKeys.GOOGLE_REFRESH_TOKEN, response.getRefreshToken());
        }
        if (response.getExpiresInSeconds() != null) {
            long expiryMs = System.currentTimeMillis() + response.getExpiresInSeconds() * 1000L;
            settings.set(AppSettingKeys.GOOGLE_TOKEN_EXPIRY_MS, Long.toString(expiryMs));
        }
    }

    public void clear() {
        settings.delete(AppSettingKeys.GOOGLE_ACCESS_TOKEN);
        settings.delete(AppSettingKeys.GOOGLE_REFRESH_TOKEN);
        settings.delete(AppSettingKeys.GOOGLE_TOKEN_EXPIRY_MS);
    }

    public Optional<Credential> loadCredential() {
        var clientId = settings.get(AppSettingKeys.GOOGLE_CLIENT_ID).orElse(null);
        var clientSecret = settings.get(AppSettingKeys.GOOGLE_CLIENT_SECRET).orElse(null);
        var refreshToken = settings.get(AppSettingKeys.GOOGLE_REFRESH_TOKEN).orElse(null);
        if (clientId == null || clientSecret == null || refreshToken == null) {
            return Optional.empty();
        }

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setTokenServerUrl(new GenericUrl(GoogleOAuthConstants.TOKEN_SERVER_URL))
                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
                .addRefreshListener(new PersistingRefreshListener())
                .build();

        credential.setAccessToken(settings.get(AppSettingKeys.GOOGLE_ACCESS_TOKEN).orElse(null));
        credential.setRefreshToken(refreshToken);
        settings.get(AppSettingKeys.GOOGLE_TOKEN_EXPIRY_MS)
                .map(Long::parseLong)
                .ifPresent(credential::setExpirationTimeMilliseconds);

        return Optional.of(credential);
    }

    private final class PersistingRefreshListener implements CredentialRefreshListener {
        @Override
        public void onTokenResponse(Credential credential, TokenResponse tokenResponse) {
            log.debug("Google access token refreshed; persisting new value");
            saveFromResponse(tokenResponse);
        }

        @Override
        public void onTokenErrorResponse(Credential credential, TokenErrorResponse errorResponse) {
            log.warn("Google token refresh failed: {} ({})",
                    errorResponse.getError(), errorResponse.getErrorDescription());
        }
    }
}
