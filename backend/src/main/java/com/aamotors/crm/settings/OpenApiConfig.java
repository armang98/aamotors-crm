package com.aamotors.crm.settings;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "A&A Motors CRM API", version = "0.1.0",
        description = "Backend API for the A&A Motors CRM (single-tenant, ADMIN only)."))
public class OpenApiConfig {
}
