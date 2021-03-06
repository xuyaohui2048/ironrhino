package org.ironrhino.core.service;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.ironrhino.core.configuration.DataSourceConfiguration;
import org.ironrhino.core.hibernate.SessionFactoryBean;
import org.ironrhino.core.hibernate.dialect.MyDialectResolver;
import org.ironrhino.core.spring.configuration.CommonConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ComponentScan(basePackageClasses = { SessionFactoryBean.class, EntityManager.class })
@EnableTransactionManagement(proxyTargetClass = true, order = 0)
@Import(DataSourceConfiguration.class)
public class HibernateConfiguration extends CommonConfiguration {

	@Value("${annotatedClasses}")
	private Class<?>[] annotatedClasses;

	@Bean
	public SessionFactoryBean sessionFactory(DataSource dataSource) {
		SessionFactoryBean sfb = new SessionFactoryBean();
		sfb.setDataSource(dataSource);
		sfb.setAnnotatedClasses(annotatedClasses);
		sfb.getHibernateProperties().setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop");
		sfb.getHibernateProperties().setProperty(AvailableSettings.DIALECT_RESOLVERS,
				MyDialectResolver.class.getName());
		return sfb;
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource, SessionFactory sessionFactory) {
		HibernateTransactionManager tm = new HibernateTransactionManager();
		tm.setDataSource(dataSource);
		tm.setSessionFactory(sessionFactory);
		return tm;
	}

}
