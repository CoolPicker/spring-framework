package org.springframework.nya.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Description 测试循环依赖X
 * @Author nya
 * @Date 2020/9/28 下午4:41
 **/
@Component
public class X {

	@Autowired
	private Y y;

	public X() {
		System.out.println("x created");
	}

	public void sayHello(){
		System.out.println("hello");
	}
}
