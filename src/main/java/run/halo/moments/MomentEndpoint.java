package run.halo.moments;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.schema.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.moments.service.MomentService;

/**
 * A custom endpoint for {@link run.halo.moments.Moment}.
 *
 * @author LIlGG
 * @since 1.0.0
 */
@Component
@AllArgsConstructor
public class MomentEndpoint implements CustomEndpoint {

    private final MomentService momentService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.moment.halo.run/v1alpha1/Moment";
        return SpringdocRouteBuilder.route()
            .GET("moments", this::listMoment, builder -> {
                builder.operationId("ListMoments")
                    .description("List moments.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementation(ListResult.generateGenericClass(ListedMoment.class))
                    );
                MomentQuery.buildParameters(builder);
            })
            .GET("moments/{name}", this::getMoment,
                builder -> builder.operationId("GetMoment")
                    .description("Get a moment by name.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Moment name")
                        .required(true)
                        .implementation(String.class)
                    )
                    .response(responseBuilder()
                        .implementation(ListedMoment.class)
                    ))
            .GET("tags", this::listMyTags,
                builder -> builder.operationId("ListTags")
                    .description("List all moment tags.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.QUERY)
                        .description("Tag name to query")
                        .required(false)
                        .implementation(String.class)
                    )
                    .response(responseBuilder()
                        .implementationArray(String.class)
                    ))
            .POST("moments", this::createMoment,
                builder -> builder.operationId("CreateMoment")
                    .description("Create a Moment.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(Builder.schemaBuilder()
                                .implementation(Moment.class))
                        ))
                    .response(responseBuilder()
                        .implementation(Moment.class))
            )
            .PUT("moments/{name}/pin", this::pinMoment,
                builder -> builder.operationId("PinMoment")
                    .description("Pin a moment to the top.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .required(true)
                        .implementation(String.class))
                    .response(responseBuilder()
                        .implementation(Moment.class))
            )
            .PUT("moments/{name}/unpin", this::unpinMoment,
                builder -> builder.operationId("UnpinMoment")
                    .description("Remove pin from a moment.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .required(true)
                        .implementation(String.class))
                    .response(responseBuilder()
                        .implementation(Moment.class))
            )
            .PUT("moments/-/pin-order", this::reorderPinned,
                builder -> builder.operationId("ReorderPinnedMoments")
                    .description("Reorder pinned moments. "
                        + "The first name in the list will become the top one.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(Builder.schemaBuilder()
                                .implementation(NamesRequest.class))
                        ))
                    .response(responseBuilder().implementation(Map.class))
            )
            .POST("moments/-/delete-batch", this::deleteBatch,
                builder -> builder.operationId("DeleteMomentsBatch")
                    .description("Delete moments in batch by names.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(Builder.schemaBuilder()
                                .implementation(NamesRequest.class))
                        ))
                    .response(responseBuilder().implementation(Map.class))
            )
            .POST("moments/-/approve-batch", this::approveBatch,
                builder -> builder.operationId("ApproveMomentsBatch")
                    .description("Approve moments in batch by names.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(Builder.schemaBuilder()
                                .implementation(NamesRequest.class))
                        ))
                    .response(responseBuilder().implementation(Map.class))
            )
            .POST("moments/-/visible-batch", this::setVisibleBatch,
                builder -> builder.operationId("SetMomentsVisibleBatch")
                    .description("Update visibility of moments in batch by names.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(Builder.schemaBuilder()
                                .implementation(VisibleBatchRequest.class))
                        ))
                    .response(responseBuilder().implementation(Map.class))
            )
            .build();
    }

    private Mono<ServerResponse> getMoment(ServerRequest request) {
        var name = request.pathVariable("name");
        return momentService.findMomentByName(name)
            .flatMap(moment -> ServerResponse.ok().bodyValue(moment));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.moment.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> createMoment(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(Moment.class)
            .map(moment -> {
                moment.getSpec().setApproved(true);
                moment.getSpec().setApprovedTime(Instant.now());
                return moment;
            })
            .flatMap(momentService::create)
            .flatMap(moment -> ServerResponse.ok().bodyValue(moment));
    }

    private Mono<ServerResponse> listMoment(ServerRequest serverRequest) {
        MomentQuery query = new MomentQuery(serverRequest.exchange());
        return momentService.listMoment(query)
            .flatMap(listedMoments -> ServerResponse.ok().bodyValue(listedMoments));
    }

    private Mono<ServerResponse> listMyTags(ServerRequest request) {
        String name = request.queryParam("name").orElse(null);
        return getCurrentUser()
            .map(username -> new MomentQuery(request.exchange(), username))
            .flatMapMany(momentService::listAllTags)
            .filter(tagName -> StringUtils.isBlank(name) || StringUtils.containsIgnoreCase(tagName,
                name))
            .collectList()
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> pinMoment(ServerRequest request) {
        var name = request.pathVariable("name");
        return momentService.setPinned(name, true)
            .flatMap(moment -> ServerResponse.ok().bodyValue(moment));
    }

    private Mono<ServerResponse> unpinMoment(ServerRequest request) {
        var name = request.pathVariable("name");
        return momentService.setPinned(name, false)
            .flatMap(moment -> ServerResponse.ok().bodyValue(moment));
    }

    private Mono<ServerResponse> reorderPinned(ServerRequest request) {
        return request.bodyToMono(NamesRequest.class)
            .flatMap(body -> momentService.reorderPinned(body.getNames())
                .then(ServerResponse.ok()
                    .bodyValue(Map.of("total",
                        body.getNames() == null ? 0 : body.getNames().size()))));
    }

    private Mono<ServerResponse> deleteBatch(ServerRequest request) {
        return request.bodyToMono(NamesRequest.class)
            .flatMap(body -> momentService.deleteBatch(body.getNames())
                .flatMap(count -> ServerResponse.ok().bodyValue(Map.of("deleted", count))));
    }

    private Mono<ServerResponse> approveBatch(ServerRequest request) {
        return request.bodyToMono(NamesRequest.class)
            .flatMap(body -> momentService.approveBatch(body.getNames())
                .flatMap(count -> ServerResponse.ok().bodyValue(Map.of("updated", count))));
    }

    private Mono<ServerResponse> setVisibleBatch(ServerRequest request) {
        return request.bodyToMono(VisibleBatchRequest.class)
            .flatMap(body -> momentService
                .setVisibleBatch(body.getNames(), body.getVisible())
                .flatMap(count -> ServerResponse.ok().bodyValue(Map.of("updated", count))));
    }

    private Mono<String> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName);
    }

    @Data
    public static class NamesRequest {
        private List<String> names;
    }

    @Data
    public static class VisibleBatchRequest {
        private List<String> names;
        private Moment.MomentVisible visible;
    }
}
