package com.ninsky.cronos.account.fiscal.api;

import com.ninsky.cronos.account.fiscal.application.GetMyFiscalDataUseCase;
import com.ninsky.cronos.account.fiscal.application.UpsertMyFiscalDataUseCase;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.fiscal.infrastructure.FiscalDataMapper;
import com.ninsky.cronos.account.shared.api.AccountMessages;
import com.ninsky.cronos.account.shared.api.ETags;
import com.ninsky.cronos.account.shared.api.OpenApiExamples;
import com.ninsky.cronos.application.response.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/** Fiscal data is sensitive: every response is {@code Cache-Control: no-store}. */
@Validated
@RestController
@RequestMapping(value = "/users/me/fiscal", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account Settings")
public class FiscalDataController {

    private final GetMyFiscalDataUseCase getMyFiscalDataUseCase;
    private final UpsertMyFiscalDataUseCase upsertMyFiscalDataUseCase;
    private final FiscalDataMapper fiscalDataMapper;
    private final AccountMessages messages;

    @GetMapping
    @Operation(summary = "Get my fiscal data", description = "200 with data null when not registered yet (never 404).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Fiscal data, or data null",
                    content = @Content(examples = {
                            @ExampleObject(name = "registered", value = OpenApiExamples.FISCAL_RESPONSE),
                            @ExampleObject(name = "notRegistered", value = OpenApiExamples.NULL_DATA)}))
    })
    public ResponseEntity<ApiResponse<FiscalDataResponse>> getMyFiscalData() {
        Optional<FiscalData> fiscalData = getMyFiscalDataUseCase.execute();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        fiscalData.map(FiscalData::version).ifPresent(version -> response.eTag(ETags.of(version)));
        return response.body(fiscalData
                .map(data -> ApiResponse.success(messages.get("account.fiscal.retrieved"), fiscalDataMapper.toResponse(data)))
                .orElseGet(() -> ApiResponse.success(messages.get("account.fiscal.notRegistered"), null)));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or replace my fiscal data",
            description = "Upsert. taxpayerType is derived from the RFC and must not be sent. Validated against SAT / CFDI 4.0 rules.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Saved",
                    content = @Content(examples = @ExampleObject(name = "saved", value = OpenApiExamples.FISCAL_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (all fields at once)",
                    content = @Content(examples = @ExampleObject(name = "invalid", value = OpenApiExamples.FISCAL_VALIDATION_ERROR))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Concurrent modification"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "412", description = "If-Match does not match the current version"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "More than 30 fiscal writes per hour")
    })
    public ResponseEntity<ApiResponse<FiscalDataResponse>> upsertMyFiscalData(
            @Parameter(in = ParameterIn.HEADER, description = "Optional optimistic-concurrency precondition (ETag from GET)")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "request", value = OpenApiExamples.FISCAL_REQUEST)))
            @Valid @RequestBody UpsertFiscalDataRequest request) {
        FiscalData saved = upsertMyFiscalDataUseCase.execute(fiscalDataMapper.toCommand(request, ETags.parseIfMatch(ifMatch)));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .eTag(ETags.of(saved.version()))
                .body(ApiResponse.success(messages.get("account.fiscal.saved"), fiscalDataMapper.toResponse(saved)));
    }
}
