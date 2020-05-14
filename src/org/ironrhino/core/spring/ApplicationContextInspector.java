package org.ironrhino.core.spring;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.ironrhino.core.util.ClassScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.function.SingletonSupplier;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

import com.opensymphony.xwork2.ActionSupport;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ApplicationContextInspector {

	@Autowired
	private ConfigurableListableBeanFactory ctx;

	@Autowired
	private ConfigurableEnvironment env;

	private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

	private SingletonSupplier<Map<String, ApplicationProperty>> overridenPropertiesSupplier = SingletonSupplier
			.of(() -> {
				Map<String, ApplicationProperty> overridenProperties = new TreeMap<>();
				for (PropertySource<?> ps : env.getPropertySources()) {
					addOverridenProperties(overridenProperties, ps);
				}
				return Collections.unmodifiableMap(overridenProperties);
			});

	private SingletonSupplier<Map<String, ApplicationProperty>> defaultPropertiesSupplier = SingletonSupplier.of(() -> {
		Map<String, Set<String>> props = new HashMap<>();
		for (String s : ctx.getBeanDefinitionNames()) {
			BeanDefinition bd = ctx.getBeanDefinition(s);
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
		} catch (IOException e) {
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
	});

	public Map<String, ApplicationProperty> getOverridenProperties() {
		return overridenPropertiesSupplier.obtain();
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
		EmbeddedValueResolver resolver = new EmbeddedValueResolver(ctx);
		if (element.getTagName().equals("import")) {
			try {
				Resource[] resources = resourcePatternResolver
						.getResources(resolver.resolveStringValue(element.getAttribute("resource")));
				for (Resource r : resources)
					add(r, props);
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
							Resource[] resources = resourcePatternResolver
									.getResources(resolver.resolveStringValue(ele.getAttribute("value")));
							for (Resource r : resources)
								add(r, props);
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

}