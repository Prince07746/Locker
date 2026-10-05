package com.guardian.app.crypto;

import com.guardian.app.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class CryptoInitializer {

    private final AppProperties props;

    public CryptoInitializer(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        CryptoKeys.init(props.cryptoKey(), props.emailHmacKey());
    }
}
