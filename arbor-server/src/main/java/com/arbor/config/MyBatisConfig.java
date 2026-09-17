package com.arbor.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis 手动配置（Spring Boot 4.0.0 与 mybatis-spring-boot-starter 3.0.4 兼容性适配）
 * <p>
 * 使用 MybatisSqlSessionFactoryBean（MP 扩展）确保分页插件和租户插件正常拦截。
 * </p>
 *
 * @author gwl
 */
@Configuration
public class MyBatisConfig {

    /** Mapper XML 文件扫描路径 */
    private static final String MAPPER_LOCATIONS = "classpath:mapper/*.xml";

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor interceptor) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        // 配置 XML Mapper 文件扫描路径
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources(MAPPER_LOCATIONS));

        // 配置驼峰命名映射：create_time → createTime
        com.baomidou.mybatisplus.core.MybatisConfiguration config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        // 将 MP 拦截器直接加入 Configuration
        config.addInterceptor(interceptor);
        factory.setConfiguration(config);

        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
