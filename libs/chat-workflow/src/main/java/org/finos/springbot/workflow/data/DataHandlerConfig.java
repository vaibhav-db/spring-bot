package org.finos.springbot.workflow.data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.finos.springbot.ChatWorkflowConfig;
import org.finos.springbot.entityjson.EntityJson;
import org.finos.springbot.entityjson.VersionSpace;
import org.finos.springbot.workflow.annotations.Work;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class DataHandlerConfig {

	private static final Logger LOG = LoggerFactory.getLogger(DataHandlerConfig.class);

	@Autowired
	ApplicationContext ac;
	
	protected List<VersionSpace> scanForWorkClasses() {
		return scanForWorkClasses(ac);
	}
	
	@Bean
	@ConditionalOnMissingBean
	public EntityJsonConverter entityJsonConverter() {
		List<VersionSpace> workAnnotatedversionSpaces = scanForWorkClasses();
		
		ObjectMapper om = new ObjectMapper();
		TypeFactory tf = TypeFactory.defaultInstance()
                .withClassLoader(ac.getClassLoader());
		om.setTypeFactory(tf);
		om.enable(SerializationFeature.INDENT_OUTPUT);
		om.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		om.registerModule(new JavaTimeModule());
				
		return new EntityJsonConverter(om, workAnnotatedversionSpaces);
	}
	
	
	public static List<VersionSpace> scanForWorkClasses(ApplicationContext ac) {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Work.class));
		Set<BeanDefinition> toAdd = scanner.findCandidateComponents(getPackageName(ChatWorkflowConfig.class));
		
		for (String ent : ac.getBeanNamesForAnnotation(SpringBootApplication.class)) {
			String packageName = getPackageName(ac.getBean(ent).getClass());
			Set<BeanDefinition> user = scanner.findCandidateComponents(packageName);
			toAdd.addAll(user);
		}
		
		List<VersionSpace> versionSpaces = toAdd.stream()
			.map(bd -> bd.getBeanClassName()) 
			.map(s -> {
				try {
					return Class.forName(s);
				} catch (ClassNotFoundException e) {
					LOG.error("Couldn't instantiate: "+s, e);
					return null;
				}
			})
			.filter(x -> x != null) 
			.flatMap(c -> {
				Work w = c.getAnnotation(Work.class);
				String jsonTypeName[] = w.jsonTypeName();
				return IntStream.range(0, jsonTypeName.length)
						.mapToObj(i -> {
							String t = jsonTypeName[i];
							if (i == 0) {
								t = StringUtils.hasText(t) ? t : EntityJson.getEntityJsonTypeName(c);
								String writeVersion = w.writeVersion();
								String[] readVersions = w.readVersions();
								return new VersionSpace(t, c, writeVersion, readVersions);
							} else {
								String[] readVersions = w.readVersions();
								return new VersionSpace(t, c, null, readVersions);
							}
						});
				})
			.collect(Collectors.toList());
		return versionSpaces;
	}

	private static String getPackageName(Class<?> c) {
		String cn = c.getName();
        int dot = cn.lastIndexOf('.');
        String pn = (dot != -1) ? cn.substring(0, dot).intern() : "";
        return pn;
	}

}
