package com.buffnerf.admin;

import com.buffnerf.TestcontainersConfiguration;
import com.buffnerf.domain.GameRepository;
import com.buffnerf.domain.Patch;
import com.buffnerf.domain.PatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "admin.key=secret")
class AdminApiTests {

	@Autowired WebApplicationContext ctx;
	@Autowired AdminKeyFilter filter;
	@Autowired GameRepository games;
	@Autowired PatchRepository patches;
	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(ctx).addFilters(filter).build();
	}

	@Test
	void rejectsMissingOrWrongKey() throws Exception {
		mvc.perform(get("/api/admin/patches?status=READY")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/patches?status=READY").header("X-Admin-Key", "wrong"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void manualContentMakesPatchReady() throws Exception {
		Patch p = patches.save(new Patch(games.findAll().getFirst(), "admin-test", "t", Instant.now()));

		mvc.perform(put("/api/admin/patches/" + p.getId() + "/content").header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
				.andExpect(status().isBadRequest());

		mvc.perform(put("/api/admin/patches/" + p.getId() + "/content").header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"아트록스 공격력 60 → 62\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contentStatus").value("READY"))
				.andExpect(jsonPath("$.contentSource").value("MANUAL"));

		mvc.perform(put("/api/admin/patches/999999/content").header("X-Admin-Key", "secret")
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
				.andExpect(status().isNotFound());
	}
}
