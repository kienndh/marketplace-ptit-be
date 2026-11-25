package com.ptiters.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class MarketplaceApplication {

	public static void main(String[] args) {
		// Python lưu/đọc theo UTC; ép JVM về UTC để mọi timestamp nhất quán
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(MarketplaceApplication.class, args);
	}

}
