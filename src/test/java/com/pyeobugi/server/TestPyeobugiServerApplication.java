package com.pyeobugi.server;

import org.springframework.boot.SpringApplication;

public class TestPyeobugiServerApplication {

	public static void main(String[] args) {
		SpringApplication.from(PyeobugiServerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
