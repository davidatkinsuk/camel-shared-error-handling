package uk.co.davidatkins;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ErrorPropagationSolutionTest extends CamelTestSupport {

    private static class GenericException extends Exception {
    }

    private static class RouteSpecificException extends Exception {
    }

    private static class CommonErrorHandlers extends RouteBuilder {

        public void addGenericExceptionHandler(RouteBuilder routeBuilder) {

            routeBuilder.onException(GenericException.class)
                    .handled(true)
                    .log("GenericException Handled")
                    .setBody(constant("GenericException Handled"));

        }

        @Override
        public void configure() throws Exception {
            // required by super class
        }
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {

        List<RouteBuilder> routes = new ArrayList<RouteBuilder>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                new CommonErrorHandlers().addGenericExceptionHandler(this);

                // @formatter:off

                from("direct:main-route")
                        .log("Entering main-route")
                        .choice()
                        .when().simple("${body} == 'Throw GenericException in main-route'")
                            .log("GenericException Thrown!")
                            .throwException(new GenericException())
                        .endChoice()
                        .end()
                        .to("direct:sub-route-specific-error-handler")
                        .setBody(constant("No Exception"));

                // @formatter:on

            }
        });

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                new CommonErrorHandlers().addGenericExceptionHandler(this);

                // @formatter:off

                // some other exception
                onException(RouteSpecificException.class)
                        .handled(true)
                        .log("RouteSpecificException Handled in Sub")
                        .setBody(constant("RouteSpecificException Handled in Sub"));

                from("direct:sub-route-specific-error-handler")
                        .log("Entering sub-route-specific-error-handler")
                                // note that i've removed .errorHandler(noErrorHandler())
                        .choice()
                            .when().simple("${body} == 'Throw GenericException in sub-route-specific-error-handler'")
                                .log("GenericException Thrown!")
                                .throwException(new GenericException())
                            .when().simple("${body} == 'Throw RouteSpecificException in sub-route-specific-error-handler'")
                                .log("RouteSpecificException thrown")
                            .throwException(new RouteSpecificException())
                            .endChoice()
                        .end();

                // @formatter:on


            }
        });

        return routes.toArray(new RouteBuilder[routes.size()]);
    }

    @Test
    public void ifExceptionInMain_HandledByMainHandler() {
        String result = template().requestBody("direct:main-route", "Throw GenericException in main-route", String.class);
        assertEquals("GenericException Handled", result);
    }

    // TODO: prove that other error handler is used
    @Test
    public void ifGenericExceptionInSubRoute_HandledByMainHandler() {
        String result = template().requestBody("direct:main-route", "Throw GenericException in sub-route-specific-error-handler", String.class);
        assertEquals("GenericException Handled", result);
    }

    @Test
    public void ifRouteSpecificExceptionInSubRoute_HandledByMainHandler() {
        String result = template().requestBody("direct:main-route", "Throw RouteSpecificException in sub-route-specific-error-handler", String.class);
        assertEquals("RouteSpecificException Handled in Sub", result);
    }

}
