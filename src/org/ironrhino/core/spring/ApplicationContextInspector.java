package org.ironrhino.core.spring;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.ironrhino.core.fs.impl.FtpFileStorage;
import org.ironrhino.core.servlet.ServletContainerHelper;
import org.ironrhino.core.util.AppInfo;
import org.ironrhino.core.util.ClassScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.opensymphony.xwork2.ActionSupport;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ApplicationContextInspector {

	@Autowired
	private ConfigurableApplicationContext ctx;

	@Autowired
	private ConfigurableEnvironment env;

	private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

	private SingletonSupplier<Map<String, ApplicationProperty>> overridenPropertiesSupplier = SingletonSupplier
			.of(this::computeOverridenProperties);

	private SingletonSupplier<Map<String, ApplicationProperty>> defaultPropertiesSupplier = SingletonSupplier
			.of(this::computeDefaultProperties);

	private SingletonSupplier<ServerMap> serverMapSupplier = SingletonSupplier.of(this::computeServerMap);

	public Map<String, ApplicationProperty> getOverridenProperties() {
		return overridenPropertiesSupplier.obtain();
	}

	private Map<String, ApplicationProperty> computeOverridenProperties() {
		Map<String, ApplicationProperty> overridenProperties = new TreeMap<>();
		for (PropertySource<?> ps : env.getPropertySources()) {
			addOverridenProperties(overridenProperties, ps);
		}
		return Collections.unmodifiableMap(overridenProperties);

	}

	private void addOverridenProperties(Map<String, ApplicationProperty> properties, PropertySource<?> propertySource) {
		String name = propertySource.getName();
		if (name != null && name.startsWith("servlet"))
			return;
		if (propertySource instanceof EnumerablePropertySource) {
			EnumerablePropertySource<?> ps = (EnumerablePropertySource<?>) propertySource;
			for (String s : ps.getPropertyNames()) {
				if (!(propertySource instanceof ResourcePropertySource) && !getDefaultProperties().containsKey(s))
					continue;
				if (!properties.containsKey(s)) {
					ApplicationProperty ap = new ApplicationProperty(
							s.endsWith(".password") ? "********" : String.valueOf(ps.getProperty(s)));
					ap.getSources().add(name);
					properties.put(s, ap);
				}
			}
		} else if (propertySource instanceof CompositePropertySource) {
			for (PropertySource<?> ps : ((CompositePropertySource) propertySource).getPropertySources()) {
				addOverridenProperties(properties, ps);
			}
		}
	}

	public Map<String, ApplicationProperty> getDefaultProperties() {
		return defaultPropertiesSupplier.obtain();
	}

	private Map<String, ApplicationProperty> computeDefaultProperties() {

		Map<String, Set<String>> props = new HashMap<>();
		for (String s : ctx.getBeanDefinitionNames()) {
			BeanDefinition bd = ctx.getBeanFactory().getBeanDefinition(s);
			String clz = bd.getBeanClassName();
			if (clz == null) {
				continue;
			}
			try {
				Class<?> clazz = Class.forName(clz);
				ReflectionUtils.doWithFields(clazz, field -> {
					props.computeIfAbsent(field.getAnnotation(Value.class).value(), k -> new TreeSet<>())
							.add(formatClassName(field.getDeclaringClass()));
				}, field -> {
					return field.isAnnotationPresent(Value.class);
				});
				ReflectionUtils.doWithMethods(clazz, method -> {
					props.computeIfAbsent(method.getAnnotation(Value.class).value(), k -> new TreeSet<>())
							.add(formatClassName(method.getDeclaringClass()));
				}, method -> {
					return method.isAnnotationPresent(Value.class);
				});
			} catch (NoClassDefFoundError e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		try {
			for (Resource resource : resourcePatternResolver
					.getResources("classpath*:resources/spring/applicationContext-*.xml"))
				add(resource, props);
		} catch (

		IOException e) {
			throw new RuntimeException(e);
		}
		for (Class<?> clazz : ClassScanner.scanAssignable(ClassScanner.getAppPackages(), ActionSupport.class)) {
			ReflectionUtils.doWithFields(clazz, field -> {
				props.computeIfAbsent(field.getAnnotation(Value.class).value(), k -> new TreeSet<>())
						.add(formatClassName(field.getDeclaringClass()));
			}, field -> {
				return field.isAnnotationPresent(Value.class);
			});
			ReflectionUtils.doWithMethods(clazz, method -> {
				props.computeIfAbsent(method.getAnnotation(Value.class).value(), k -> new TreeSet<>())
						.add(formatClassName(method.getDeclaringClass()));
			}, method -> {
				return method.isAnnotationPresent(Value.class);
			});
		}
		Map<String, ApplicationProperty> defaultProperties = new TreeMap<>();
		props.forEach((k, v) -> {
			int start = k.indexOf("${");
			int end = k.lastIndexOf("}");
			if (start > -1 && end > start) {
				k = k.substring(start + 2, end);
				String[] arr = k.split(":", 2);
				if (arr.length > 1) {
					ApplicationProperty ap = new ApplicationProperty(arr[1]);
					ap.getSources().addAll(v);
					defaultProperties.put(arr[0], ap);
				}
			}
		});

		ctx.getBeanProvider(DefaultPropertiesProvider.class).forEach(p -> p.getDefaultProperties().forEach((k, v) -> {
			ApplicationProperty ap = defaultProperties.computeIfAbsent(k, s -> new ApplicationProperty(v));
			ap.getSources().add(formatClassName(org.ironrhino.core.util.ReflectionUtils.getActualClass(p.getClass())));
		}));

		defaultProperties.forEach((k, v) -> {
			String definedSource = v.getDefinedSource();
			if (definedSource.indexOf(",") > 0) {
				log.warn("'{}' is defined in multiple sources {}, please consider create a specified config bean", k,
						definedSource);
			}
		});
		return Collections.unmodifiableMap(defaultProperties);

	}

	DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();

	private void add(Resource resource, Map<String, Set<String>> props) {
		if (resource.isReadable())
			try (InputStream is = resource.getInputStream()) {
				Document doc = builderFactory.newDocumentBuilder().parse(new InputSource(is));
				add(resource, doc.getDocumentElement(), props);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
	}

	private void add(Resource resource, Element element, Map<String, Set<String>> props) {
		EmbeddedValueResolver resolver = new EmbeddedValueResolver(ctx.getBeanFactory());
		if (element.getTagName().equals("import")) {
			try {
				String resovled = resolver.resolveStringValue(element.getAttribute("resource"));
				if (resovled != null) {
					Resource[] resources = resourcePatternResolver.getResources(resovled);
					for (Resource r : resources)
						add(r, props);
				}
			} catch (IOException e) {
			}
			return;
		}
		if ("org.springframework.batch.core.configuration.support.ClasspathXmlApplicationContextsFactoryBean"
				.equals(element.getAttribute("class"))) {
			for (int i = 0; i < element.getChildNodes().getLength(); i++) {
				Node node = element.getChildNodes().item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if ("resources".equals(ele.getAttribute("name"))) {
						try {
							String resovled = resolver.resolveStringValue(ele.getAttribute("value"));
							if (resovled != null) {
								Resource[] resources = resourcePatternResolver.getResources(resovled);
								for (Resource r : resources)
									add(r, props);
							}
						} catch (IOException e) {
						}
						return;
					}
				}
			}
		}
		NamedNodeMap map = element.getAttributes();
		for (int i = 0; i < map.getLength(); i++) {
			Attr attr = (Attr) map.item(i);
			if (attr.getValue().contains("${"))
				props.computeIfAbsent(attr.getValue(), k -> new TreeSet<>()).add(resource.toString());
		}
		for (int i = 0; i < element.getChildNodes().getLength(); i++) {
			Node node = element.getChildNodes().item(i);
			if (node instanceof Text) {
				Text text = (Text) node;
				if (text.getTextContent().contains("${"))
					props.computeIfAbsent(text.getTextContent(), k -> new TreeSet<>()).add(resource.toString());
			} else if (node instanceof Element) {
				add(resource, (Element) node, props);
			}
		}
	}

	private static String formatClassName(Class<?> clazz) {
		return String.format("class [%s]", clazz.getCanonicalName());
	}

	public ServerMap getServerMap() {
		return serverMapSupplier.obtain();
	}

	private ServerMap computeServerMap() {
		ServerMap sm = new ServerMap(AppInfo.getAppName());

		// JDK
		sm.getServices().add(new Service("JDK", System.getProperty("java.version"), null));

		// servlet container
		if (ctx instanceof WebApplicationContext) {
			String serverInfo = ServletContainerHelper.getServerInfo(((WebApplicationContext) ctx).getServletContext());
			if (serverInfo != null) {
				String[] arr = serverInfo.split("/");
				sm.getServices().add(new Service(arr[0], arr[1], null));
			}
		}

		// database
		ctx.getBeansOfType(DataSource.class).forEach((k, v) -> {
			if (v instanceof HikariDataSource) {
				HikariDataSource hds = (HikariDataSource) v;
				String url = hds.getJdbcUrl();
				url = url.substring(url.indexOf(':') + 1);
				String type = url.substring(0, url.indexOf(':'));
				String address = url.substring(url.indexOf("//") + 2);
				int i = address.indexOf('/');
				if (i > 0)
					address = address.substring(0, i);
				i = address.indexOf(';');
				if (i > 0)
					address = address.substring(0, i);
				String version = null;
				try (Connection c = hds.getConnection()) {
					DatabaseMetaData dbmd = c.getMetaData();
					type = dbmd.getDatabaseProductName();
					version = dbmd.getDatabaseProductVersion();
				} catch (SQLException e) {
					// Ignore
				}
				sm.getServices().add(new Service(type, version, address));
			}
		});

		// redis
		ctx.getBeansOfType(RedisConnectionFactory.class).forEach((k, v) -> {
			if (v instanceof LettuceConnectionFactory) {
				LettuceConnectionFactory cf = (LettuceConnectionFactory) v;
				String type = "Redis";
				String version = null;
				String address = null;
				RedisSentinelConfiguration sconf;
				RedisClusterConfiguration cconf;
				if ((sconf = cf.getSentinelConfiguration()) != null) {
					String master = sconf.getMaster().getName();
					address = "sentinel://"
							+ sconf.getSentinels().stream().map(Object::toString).collect(Collectors.joining(",")) + "/"
							+ master;
				} else if ((cconf = cf.getClusterConfiguration()) != null) {
					address = "cluster://"
							+ cconf.getClusterNodes().stream().map(Object::toString).collect(Collectors.joining(","));
				} else {
					RedisStandaloneConfiguration conf = cf.getStandaloneConfiguration();
					address = conf.getHostName();
					int port = conf.getPort();
					if (port > 0 && port != 6379)
						address += ':' + port;
				}
				RedisConnection connection = null;
				try {
					connection = RedisConnectionUtils.getConnection(cf);
					version = connection.info().getProperty("redis_version");
				} finally {
					if (connection != null)
						RedisConnectionUtils.releaseConnection(connection, cf, false);
				}
				sm.getServices().add(new Service(type, version, address));
			}
		});

		// ftp
		ctx.getBeansOfType(FtpFileStorage.class).forEach((k, v) -> {
			URI uri = v.getUri();
			String type = "FTP";
			String version = null;
			String address = uri.getHost();
			int port = uri.getPort();
			if (port > 0)
				address += ":" + port;
			sm.getServices().add(new Service(type, version, address));
		});

		// kafka
		if (ClassUtils.isPresent("org.springframework.kafka.annotation.EnableKafka",
				this.getClass().getClassLoader())) {
			ctx.getBeansOfType(ProducerFactory.class).forEach((k, v) -> {
				ProducerFactory<?, ?> pf = v;
				try {
					Field f = v.getClass().getField("producerFactory");
					f.setAccessible(true);
					pf = (ProducerFactory<?, ?>) f.get(v);
				} catch (Exception ex) {
					// Ignore
				}
				if (pf instanceof DefaultKafkaProducerFactory) {
					DefaultKafkaProducerFactory<?, ?> dkpf = (DefaultKafkaProducerFactory<?, ?>) pf;
					String type = "Kafka";
					String version = null;
					String address = (String) dkpf.getConfigurationProperties()
							.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
					sm.getServices().add(new Service(type, version, address));
				}
			});
			ctx.getBeansOfType(ConsumerFactory.class).forEach((k, v) -> {
				ConsumerFactory<?, ?> cf = v;
				try {
					Field f = v.getClass().getField("consumerFactory");
					f.setAccessible(true);
					cf = (ConsumerFactory<?, ?>) f.get(v);
				} catch (Exception ex) {
					// Ignore
				}
				if (cf instanceof DefaultKafkaConsumerFactory) {
					DefaultKafkaConsumerFactory<?, ?> dkcf = (DefaultKafkaConsumerFactory<?, ?>) cf;
					String type = "Kafka";
					String version = null;
					String address = (String) dkcf.getConfigurationProperties()
							.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);
					sm.getServices().add(new Service(type, version, address));
				}
			});
		}

		// elasticsearch
		String esurl = ctx.getEnvironment().getProperty("elasticsearch.url");
		if (esurl != null) {
			String type = "Elasticsearch";
			String version = null;
			String address = esurl;
			try {
				JsonNode node = new RestTemplate().getForEntity(esurl.split(",")[0], JsonNode.class).getBody();
				version = node.get("version").get("number").asText();
				sm.getServices().add(new Service(type, version, address));
			} catch (Exception ex) {
				// Ignore
			}
		}
		return sm;
	}

	@lombok.Value
	public static class ApplicationProperty {
		private String value;
		private List<String> sources = new ArrayList<>();

		public String getDefinedSource() {
			List<String> list = sources.stream().map(ApplicationProperty::extract).collect(Collectors.toList());
			if (list.size() == 1)
				return format(list.get(0));
			return format(String.join(", ", list));
		}

		public String getOverridenSource() {
			String source = sources.size() > 0 ? sources.get(0) : null;
			return format(extract(source));
		}

		private static String extract(String source) {
			if (source == null)
				return source;
			int start = source.indexOf('[');
			int end = source.indexOf(']');
			if (start > 0 && end > start) {
				String s = source.substring(start + 1, end);
				int index;
				if ((index = s.indexOf("/WEB-INF/")) > 0) {
					s = s.substring(index);
				} else {
					String home = System.getProperty("user.home");
					if (home != null && (index = s.indexOf(home)) > 0)
						s = "~" + s.substring(index + home.length());
				}
				return s;
			}
			return source;
		}

		private static String format(String sources) {
			return String.format("[%s]", sources);
		}
	}

	@lombok.Value
	public static class ServerMap {
		private String name;
		private Collection<Service> services = new LinkedHashSet<>();
	}

	@lombok.Value
	public static class Service {
		private String type;
		private String version;
		private String address;
	}

}