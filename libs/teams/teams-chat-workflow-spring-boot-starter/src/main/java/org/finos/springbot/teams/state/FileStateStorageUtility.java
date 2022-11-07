package org.finos.springbot.teams.state;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FileStateStorageUtility {

	public static Set<File> getAllDataFiles(String filePath) {
		Set<File> fileList = new HashSet<>();
		getAllAddressableFiles(new File(filePath), fileList);
		return fileList;
	}

	private static void getAllAddressableFiles(File node, Set<File> fileList) {
		if (node.isDirectory()) {
			String[] subNote = node.list();
			for (String fileName : subNote) {
				File dir = new File(node, fileName);
				if (dir.isDirectory()) {
					getAllAddressableFiles(dir, fileList);
				} else {
					if (dir.getParentFile().getName().equals(FileStateStorage.DATA_FOLDER)
							&& dir.getName().equals(TeamsStateStorage.ADDRESSABLE_KEY + FileStateStorage.FILE_EXT)) {
						fileList.add(dir);
					}
				}
			}
		}

	}

	public static Map<String, List<File>> getAllTagIndexFiles(String filePath, List<String> tags) {
		Map<String, List<File>> fileList = new HashMap<>();
		getAllTagsFiles(new File(filePath), fileList, tags);
		return fileList;
	}

	public static Map<String, List<File>> getAllTagIndexFiles(String filePath, List<String> tags,
			String addressableId) {
		List<File> list = tags.stream().map(t -> new File(filePath + File.separator + addressableId + File.separator
				+ FileStateStorage.TAG_INDEX_FOLDER + File.separator + t)).collect(Collectors.toList());
		Map<String, List<File>> fileList = new HashMap<>();
		fileList.computeIfAbsent(addressableId, k -> new ArrayList<>()).addAll(list);
		return fileList;
	}

	private static void getAllTagsFiles(File node, Map<String, List<File>> fileList, List<String> tags) {
		if (node.isDirectory()) {
			String[] subNote = node.list();
			for (String fileName : subNote) {
				File dir = new File(node, fileName);

				if (tags.contains(dir.getName())
						&& dir.getParentFile().getName().equals(FileStateStorage.TAG_INDEX_FOLDER)) {
					fileList.computeIfAbsent(dir.getParentFile().getParentFile().getName(), k -> new ArrayList<>()).add(dir);
				} else if (dir.isDirectory()) {
					getAllTagsFiles(dir, fileList, tags);
				}

			}
		}

	}

}
