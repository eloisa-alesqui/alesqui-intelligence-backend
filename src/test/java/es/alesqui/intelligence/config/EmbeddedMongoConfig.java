package es.alesqui.intelligence.config;

import de.flapdoodle.embed.mongo.commands.MongodArguments;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

import com.mongodb.reactivestreams.client.MongoClient;

@Configuration
@EnableReactiveMongoRepositories(basePackages = "es.alesqui.intelligence.repository")
public class EmbeddedMongoConfig {

    /**
     * Forces auth=false on the MongodArguments bean, running after flapdoodle's
     * fixTransactionAndAuth BeanPostProcessor which enables auth when env-var
     * credentials are present. Without this, flapdoodle starts MongoDB with --auth
     * but then strips credentials from the override URI, causing every MongoDB
     * operation in tests to fail with "Unauthorized".
     */
    @Bean
    public BeanPostProcessor disableEmbeddedMongoAuth() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof MongodArguments args) {
                    return MongodArguments.builder().from(args).auth(false).build();
                }
                return bean;
            }
        };
    }

    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(MongoClient mongoClient) {
        return new ReactiveMongoTemplate(mongoClient, "postmangpt");
    }
}
