package com.spring.nya.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @Description 测试循环依赖X
 * @Author nya
 * @Date 2020/9/28 下午4:41
 **/
@Component
public class X {

	@Autowired
	private Y y;

	private static E e = new E();

	public E getE() {
		return e;
	}

	public X() {
		System.out.println("x created");
	}

	public void sayHello(){
		System.out.println("hello");
	}
}
