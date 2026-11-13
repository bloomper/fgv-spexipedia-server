package nu.fgv.spexipedia.server.config;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import nu.fgv.spexipedia.server.util.graphql.CustomScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Scalars and query limits. A spex page is one deep query (spex, characters, who played them, their photos), hence the
 * limits are wider than in the other applications.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Configuration
public class GraphqlConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(CustomScalars.INSTANT)
                .scalar(CustomScalars.VOID);
    }

    @Bean
    public MaxQueryDepthInstrumentation maxQueryDepthInstrumentation() {
        return new MaxQueryDepthInstrumentation(14);
    }

    @Bean
    public MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation() {
        return new MaxQueryComplexityInstrumentation(3_000);
    }
}
