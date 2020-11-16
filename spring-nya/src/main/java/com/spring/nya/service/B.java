package com.spring.nya.service;

import org.springframework.beans.factory.DisposableBean;

/**
 * @Description 销毁时执行
 * @Author nya
 * @Date 2020/11/4 上午10:12
 **/
public class B implements DisposableBean {
	public B() {
		System.out.println("B created");
	}

	@Override
	public void destroy() throws Exception {
		System.out.println("B destroy ---------------");
	}
}
