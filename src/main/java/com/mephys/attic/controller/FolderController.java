package com.mephys.attic.controller;

import com.mephys.attic.service.SharedFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Folder tab: the files and subfolders put by hand in the {@linkplain SharedFolder shared folder}.
 * Everybody signed in sees them and opens them; nothing is changed from the app.
 */
@RestController
class FolderController {

	/** Opened in the browser; anything else, such as web pages that could run scripts, is downloaded */
	private static final Set<String> SHOWN_TYPES = Set.of("application/pdf", "image/png", "image/jpeg", "image/gif",
			"image/webp", "text/plain", "audio/mpeg", "audio/mp4", "video/mp4", "video/webm");

	private final SharedFolder folder;

	FolderController(SharedFolder folder) {
		this.folder = folder;
	}

	@GetMapping("/folder")
	ResponseEntity<List<SharedFolder.Entry>> list() {
		// The files change on disk without the app knowing: never reuse an earlier listing
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(folder.list());
	}

	@GetMapping("/folder/file")
	ResponseEntity<Resource> file(@RequestParam String path, @RequestParam(defaultValue = "false") boolean download) {
		Path file = folder.file(path).orElse(null);
		if (file == null) {
			return ResponseEntity.notFound().build();
		}
		String name = file.getFileName().toString();
		MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
		boolean shown = !download && SHOWN_TYPES.contains(type.getType() + "/" + type.getSubtype());
		ContentDisposition disposition = (shown ? ContentDisposition.inline() : ContentDisposition.attachment())
			.filename(name, StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.contentType(shown ? type : MediaType.APPLICATION_OCTET_STREAM)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.header("X-Content-Type-Options", "nosniff")
			.cacheControl(CacheControl.noCache())
			.body(new FileSystemResource(file));
	}

}
