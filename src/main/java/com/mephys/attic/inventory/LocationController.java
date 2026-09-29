package com.mephys.attic.inventory;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LocationController {

	@GetMapping("/locations")
	List<Location> list() {
		return List.of(Location.values());
	}

}
