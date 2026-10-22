package com.example.staycontrol;

import com.mongodb.client.MongoClient;
import com.example.staycontrol.repository.HabitacionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
	"spring.mongodb.uri=mongodb://127.0.0.1:27017/staycontrol_test",
	"spring.mongodb.database=staycontrol_test",
	"app.mongodb.verify-on-startup=false",
	"app.auth.bootstrap.enabled=false"
})
class StaycontrolApplicationTests {

	@MockitoBean
	private MongoClient mongoClient;

	@MockitoBean
	private HabitacionRepository habitacionRepository;

	@Test
	void contextLoads() {
	}

}
