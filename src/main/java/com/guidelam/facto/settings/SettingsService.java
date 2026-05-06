package com.guidelam.facto.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final AppSettingRepository repository;

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findById(key).map(AppSetting::getValue);
    }

    @Transactional(readOnly = true)
    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        repository.save(setting);
    }

    @Transactional
    public void delete(String key) {
        repository.deleteById(key);
    }
}
