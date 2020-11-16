package org.springframework.nya.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @Description 测试SpringBean 生命周期
 * @Author nya
 * @Date 2020/9/29 上午9:18
 **/
@Component
public class Z implements ApplicationContextAware {
	@Autowired
	private X x;

	public Z() {
		System.out.println("Z create");
	}

	// 生命周期初始化回调方法
	@PostConstruct
	public void zInit(){
		System.out.println("call z lifecycle init callback");
	}

	// ApplicationContextAware 回调方法
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		System.out.println("call aware callback");
	}

}
