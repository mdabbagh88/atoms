package org.jboss.aerogear.unifiedpush.service.impl.file;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.ejb.Stateless;

import org.jboss.aerogear.unifiedpush.service.file.FileManager;

/**
 * A basic, non-synchronous and blocking implementation of FileManager.
 */
@Stateless
public class FileManagerImpl implements FileManager {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void save(Path path, byte[] data) {
		try {
			File directory = path.getParent().toFile();
			if (!directory.exists()) {
				boolean created = directory.mkdirs();
				if (!created) {
					throw new RuntimeException("could not create new directory " + directory.getAbsolutePath());
				}
			}
			Files.write(path, data);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] read(Path path) {
		try {
			return Files.readAllBytes(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Throw Checked exception to prevent transaction rollback when used from service layer.
	 */
	@Override
	public List<File> list(Path path, FileFilter filter) throws FileNotFoundException {
		File directory = path.toFile();
		if (!directory.exists()) {
			throw new FileNotFoundException(path + " does not exist");
		}

		if (!directory.isDirectory()) {
			throw new FileNotFoundException(path + " is not a directory");
		}

		return Arrays.asList(directory.listFiles(filter));
	}

}