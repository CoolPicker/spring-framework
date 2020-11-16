package com.spring.nya.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @Description 初始化时执行
 * @Author nya
 * @Date 2020/11/4 上午10:11
 **/
@Component
public class A implements InitializingBean, DisposableBean, ApplicationContextAware {

	public A() {
		System.out.println("A created");
	}

	@PostConstruct
	public void pre(){
		System.out.println("A post construct");
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		System.out.println("A ApplicationContext Aware");
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		System.out.println("A initializing");
	}

	@Override
	public void destroy() throws Exception {
		System.out.println("A destroy");
	}
}
