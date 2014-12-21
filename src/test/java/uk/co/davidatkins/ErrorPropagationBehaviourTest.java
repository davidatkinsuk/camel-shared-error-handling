package uk.co.davidatkins;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO: sub route with IllegalArgumentExceptionHandler without noErrorHandler
 */
public class ErrorPropagationBehaviourTest extends CamelTestSupport {

    private static class GenericException extends Exception { }
    private static class RouteSpecificException extends Exception { }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {

        List<RouteBuilder> routes = new ArrayList<RouteBuilder>();

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                onException(GenericException.class)
                        .handled(true)
                        .log("GenericException Handled")
                        .setBody(constant("GenericException Handled"));

                from("direct:main-route")
                        .log("Entering main-route")
                        .choice()
                            .when().simple("${body} == 'Throw GenericException in main-route'")
                                .log("GenericException Thrown!")
                                .throwException(new GenericException())
                            .endChoice()
                        .end()
                        .to("direct:sub-route-default-error-handler")
                        .to("direct:sub-route-no-error-handler")
                        .to("direct:sub-route-specific-error-handler")
                        .setBody(constant("No Exception"));

            }
        });

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:sub-route-default-error-handler")
                        .log("Entering sub-route-default-error-handler")
                        .choice()
                        .when().simple("${body} == 'Throw GenericException in sub-route-default-error-handler'")
                                .log("GenericException Thrown!")
                                .throwException(new GenericException())
                            .endChoice()
                        .end();

            }
        });

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:sub-route-no-error-handler")
                        .log("Entering sub-route-no-error-handler")
                        .errorHandler(noErrorHandler())
                        .choice()
                        .when().simple("${body} == 'Throw GenericException in sub-route-no-error-handler'")
                                .log("GenericException Thrown!")
                                .throwException(new GenericException())
                            .endChoice()
                        .end();

            }
        });

        routes.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                // some other exception
                onException(RouteSpecificException.class)
                        .handled(true)
                        .log("RouteSpecificException Handled in Sub")
                        .setBody(constant("RouteSpecificException Handled In Sub"));

                from("direct:sub-route-specific-error-handler")
                        .log("Entering sub-route-specific-error-handler")
                        /* Only way to add specific handler to our subroute is to remove the noErrorHandler,
                           but then it doesn't propagate other exceptions! */
                        //.errorHandler(noErrorHandler())
                        .choice()
                            .when().simple("${body} == 'Throw GenericException in sub-route-specific-error-handler'")
                                .log("GenericException Thrown!")
                                .throwException(new GenericException())
                            .when().simple("${body} == 'Throw RouteSpecificException in sub-route-specific-error-handler'")
                                .log("RouteSpecificException thrown")
                                .throwException(new RouteSpecificException())
                        .endChoice()
                        .end();

            }
        });

        return routes.toArray(new RouteBuilder[routes.size()]);
    }

    @Test
    public void ifNoExceptionThrown_RouteCompletes() {
        String result = template().requestBody("direct:main-route", "Do Nothing", String.class);
        assertEquals("No Exception", result);
    }

    @Test
    public void ifGenericExceptionInMain_HandledByMainHandler() {
        String result = template().requestBody("direct:main-route","Throw GenericException in main-route",String.class);
        assertEquals("GenericException Handled", result);
    }

    @Test
    public void ifGenericExceptionInSubRouteWithNoOnException_NotHandledByMainHandler() {
        try {
            String result = template().requestBody("direct:main-route", "Throw GenericException in sub-route-default-error-handler", String.class);
            fail();
        } catch(CamelExecutionException e) {

        }
    }

    @Test
    public void ifGenericExceptionInSubRouteWithNoErrorHandler_HandledByMainHandler() {
        String result = template().requestBody("direct:main-route","Throw GenericException in sub-route-no-error-handler",String.class);
        assertEquals("GenericException Handled", result);
    }

    @Test
    public void ifGenericExceptionInSubRouteWithNoErrorHandlerAndRouteSpecificOnException_NotHandledByMainHandler() {
        try {
            String result = template().requestBody("direct:main-route", "Throw GenericException in sub-route-specific-error-handler", String.class);
            fail();
        } catch (CamelExecutionException e) {

        }
    }

    @Test
    public void ifRouteSpecificExceptionInSubRouteWithNoErrorHandlerAndRouteSpecificOnException_HandledInSubRoute() {
        String result = template().requestBody("direct:main-route","Throw RouteSpecificException in sub-route-specific-error-handler",String.class);
        assertEquals("RouteSpecificException Handled In Sub", result);
    }

}
