package org.finos.springbot.teams.state;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.finos.springbot.teams.TeamsException;
import org.finos.springbot.teams.history.TeamsHistory;
import org.finos.springbot.workflow.data.EntityJsonConverter;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileStateStorage extends AbstractStateStorage {

	private static final Logger LOG = LoggerFactory.getLogger(FileStateStorage.class);

	final private static String DATA_FOLDER = "data";
	final private static String TAG_INDEX_FOLDER = "tag_index";
	final private static String FILE_EXT = ".txt";

	protected Map<String, String> store = new HashMap<>();
	protected Map<String, List<Pair<String, String>>> tagIndex = new HashMap<>();

	private EntityJsonConverter ejc;
	private String filePath;

	public FileStateStorage(EntityJsonConverter ejc, String filePath) {
		super();
		this.ejc = ejc;
		this.filePath = filePath;
	}

	@Override
	public void store(String file, Map<String, String> tags, Map<String, Object> data) {
		store.put(file, ejc.writeValue(data));
		if ((tags != null) && (tags.size() > 0)) {
			List<Pair<String, String>> pairtags = tags.entrySet().stream()
					.map(e -> new Pair<String, String>(e.getKey(), e.getValue())).collect(Collectors.toList());
			tagIndex.put(file, pairtags);
		} else {
			throw new TeamsException("Cannot persist data to " + file + " - no tags");

		}

		store1(file, tags, data);
	}

	public void store1(String file, Map<String, String> tags, Map<String, Object> data) {
		String addressable = getAddressable(file);
		String storageId = getStorage(file);
		try {
			Path path = checkAndCreateFolder(this.filePath + addressable);
			Path dataPath = checkAndCreateFolder(path.toString() + File.separator + DATA_FOLDER);
			Path tagPath = checkAndCreateFolder(path.toString() + File.separator + TAG_INDEX_FOLDER);

			createStorageFile(dataPath, storageId, data);

			createTagIndexFile(tags, storageId, tagPath);
		} catch (IOException e) {
			throw new TeamsException("Error while creating or getting folder " + e);
		}
	}

	@Override
	public Optional<Map<String, Object>> retrieve(String file) {
		String addressable = getAddressable(file);
		String storageId = getStorage(file);
		Optional<String> optData = readFile(
				this.filePath + addressable + File.separator + DATA_FOLDER + File.separator + storageId + FILE_EXT);
		if (optData.isPresent()) {
			return Optional.ofNullable(ejc.readValue(optData.get()));
		} else {
			return Optional.of(Collections.emptyMap());
		}
	}

	private void createTagIndexFile(Map<String, String> tags, String storageId, Path tagPath) throws IOException {
		for (Entry<String, String> e : tags.entrySet()) {
			if (Objects.nonNull(e.getValue()) && e.getValue().equals(TeamsStateStorage.PRESENT)) {
				String tagName = getAzureTag(e.getKey());
				Path tagIndexPath = checkAndCreateFolder(tagPath + File.separator + tagName);
				checkAndCreateFile(tagIndexPath + File.separator + storageId + FILE_EXT);
			}
		}
	}

	private String getAzureTag(String s) {
		return s.replaceAll("[^0-9a-zA-Z]", "_");
	}

	private String getAzurePath(String s) {
		return s.replaceAll("[^0-9a-zA-Z/]", "_");
	}

	private Optional<String> readFile(String filePath) {
		try {
			Path path = Paths.get(filePath);
			if (Files.exists(path)) {
				List<String> lines = Files.readAllLines(path);
				return Optional.of(String.join("", lines));
			} else {
				return Optional.empty();
			}
		} catch (IOException e1) {
			throw new TeamsException("Error while retrieve data " + e1);
		}
	}

	private void createStorageFile(Path dataPath, String storageId, Map<String, Object> data) throws IOException {
		Path storageFile = checkAndCreateFile(dataPath.toString() + File.separator + storageId + FILE_EXT);
		byte[] dataToByte = ejc.writeValue(data).getBytes();
		Files.write(storageFile, dataToByte);
	}

	private Path checkAndCreateFile(String file) throws IOException {
		Path path = Paths.get(file);
		if (Files.notExists(path)) {
			Files.createFile(path);
		}
		return path;
	}

	private Path checkAndCreateFolder(String pathStr) throws IOException {
		Path path = Paths.get(pathStr);
		if (Files.notExists(path)) {
			path = Files.createDirectory(path);
		}

		return path;
	}

	private String getAddressable(String file) {
		Optional<List<String>> split = splitString(file);
		if (split.isPresent()) {
			return getAzurePath(split.get().get(0));
		}
		return file;
	}

	private String getStorage(String file) {
		Optional<List<String>> split = splitString(file);
		if (split.isPresent()) {
			return split.get().get(1);
		}
		return file;
	}

	private Optional<List<String>> splitString(String s) {
		if (s.contains("/")) {
			String[] data = s.split("/");
			if (data.length > 1) {
				return Optional.of(Arrays.asList(data));
			}
		}
		return Optional.empty();
	}

	@Override
	public Iterable<Map<String, Object>> retrieve(List<Filter> tags, boolean singleResultOnly) {
		try {
			List<Map<String, Object>> out = tagIndex.entrySet().stream().filter(e -> matchEntry(e, tags))
					.map(e -> ejc.readValue(store.get(e.getKey()))).collect(Collectors.toList());

			if ((singleResultOnly) && (out.size() > 0)) {
				out = out.subList(0, 1);
			}

//			 return out;

			Map<String, Filter> filterMap = tags.stream().collect(Collectors.toMap(x -> x.key, x -> x));

			Filter addressFilter = null;
			if (filterMap.containsKey(TeamsStateStorage.ADDRESSABLE_KEY)) {
				addressFilter = filterMap.remove(TeamsStateStorage.ADDRESSABLE_KEY);

				String tagIndexFolder = getTagIndexFolder(filterMap, addressFilter);

				Path tagPath = Paths.get(tagIndexFolder);
				if (Files.isDirectory(tagPath)) {
					List<File> files = getDataFiles(addressFilter, filterMap, tagPath);

					if ((singleResultOnly) && (files.size() > 0)) {
						files = files.subList(0, 1);
					}

					return files.stream().map(f -> ejc.readValue(readFile(f.getAbsolutePath()).orElse("")))
							.collect(Collectors.toList());
				}
			}
		} catch (IOException e) {
			throw new TeamsException("Error while retrieve data " + e);
		}
		return Collections.emptyList();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List<File> getDataFiles(Filter addressFilter, Map<String, Filter> filterMap, Path tagPath)
			throws IOException {
		List<File> l;

		try (Stream<Path> stream = Files.list(tagPath)) {
			Set<Path> paths = stream.filter(file -> !Files.isDirectory(file)).collect(Collectors.toSet());
			String address = getAzurePath(addressFilter.value);
			Map<Object, Long> files = paths.stream()
					.map(p -> Paths.get(this.filePath + File.separator + address + File.separator + DATA_FOLDER
							+ File.separator + p.getFileName().toString()))
					.map(f -> new File(f.toString()))
					.filter(fileFilter(filterMap))
					.sorted(Collections.reverseOrder(Comparator.comparingLong(File::lastModified)))
					.collect(Collectors.toMap(k -> k, File::lastModified));
			l = new ArrayList(files.keySet());
			Collections.sort(l, Collections.reverseOrder(Comparator.comparingLong(File::lastModified)));
		}
		return l;
	}

	private Predicate<? super File> fileFilter(Map<String, Filter> filterMap) {
		return p -> filterMap.entrySet().stream().filter(f -> f.getKey().equals(TeamsHistory.TIMESTAMP_KEY)).map(e -> {
			
			if(e.getValue().operator.contains("==") && e.getValue().value.equals(String.valueOf(p.lastModified()))) {
				return true;
			}else if(e.getValue().operator.contains(">") && Long.valueOf(e.getValue().value) < p.lastModified()) {
				return true;
			}else if(e.getValue().operator.contains("<") && Long.valueOf(e.getValue().value) > p.lastModified()) {
				return true;
			}{
				return false;
			}
		}).findFirst().orElse(true);
		
	}

	private String getTagIndexFolder(Map<String, Filter> map, Filter addressFilter) {
		StringBuffer tagIndexFolder = new StringBuffer(
				this.filePath + File.separator + getAzurePath(addressFilter.value) + File.separator + TAG_INDEX_FOLDER);

		map.entrySet().stream().filter(m -> m.getValue().value.equals(TeamsStateStorage.PRESENT)).forEach((e) -> {
			tagIndexFolder.append(File.separator);
			tagIndexFolder.append(getAzureTag(e.getKey()));
		});
		return tagIndexFolder.toString();
	}

	private boolean matchEntry(Entry<String, List<Pair<String, String>>> e, List<Filter> tags) {
		for (Iterator<Filter> iterator = tags.iterator(); iterator.hasNext();) {
			Filter filter = (Filter) iterator.next();

			Pair<String, String> matched = getMatchedPair(e.getValue(), filter.key);
			if (matched == null) {
				return false;
			} else {
				if (!checkMatches(filter, matched.getValue1())) {
					return false;
				}
			}
		}

		return true;
	}

	private boolean checkMatches(Filter filter, String value) {
		int cmp = filter.value.compareTo(value);

		if ((filter.operator.contains("=")) && (cmp == 0)) {
			return true;
		}

		if (filter.operator.contains(">") && (cmp < 0)) {
			return true;
		}

		if (filter.operator.contains("<") && (cmp > 0)) {
			return true;
		}

		return false;
	}

	private Pair<String, String> getMatchedPair(List<Pair<String, String>> value, String key) {
		for (Pair<String, String> pair : value) {
			if (pair.getValue0().equals(key)) {
				return pair;
			}
		}

		return null;
	}

}
