package io.bruno.docs_manager.dto;

import io.bruno.docs_manager.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull(message = "status is required") DocumentStatus status) {}
