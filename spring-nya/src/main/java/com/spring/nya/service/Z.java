package com.spring.nya.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @Description 测试SpringBean 生命周期
 * 两种声明周期初始化实现 都是在构造方法之后执行, 且实现ApplicationContextAware接口执行于@PostConstruct注解之前
 * 二者都在 FactoryBean-静态成员引用类型变量 之后执行
 * 因为静态变量是在对象实例化之前执行,即调用构造函数创建bean时执行,且在构造函数执行之前.
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
