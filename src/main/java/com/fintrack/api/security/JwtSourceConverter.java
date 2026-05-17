package com.fintrack.api.security;

import com.fintrack.common.domain.SourceType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtSourceConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sourceIdClaim = jwt.getClaimAsString("source_id");
        String sourceTypeClaim = jwt.getClaimAsString("source_type");

        UUID sourceId = UUID.fromString(sourceIdClaim);
        SourceType sourceType = SourceType.valueOf(sourceTypeClaim);

        SourceIdentity identity = new SourceIdentity(sourceId, sourceType);

        return new UsernamePasswordAuthenticationToken(identity, jwt, authoritiesConverter.convert(jwt));
    }
}
