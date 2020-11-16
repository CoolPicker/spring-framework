package org.springframework.nya;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.nya.service.Y;

/**
 * @Description 启动类
 * @Author nya
 * @Date 2020/9/27 下午5:00
 **/
public class AppStarter {

	public static void main(String[] args) {

		// 实例化ApplicationContext容器对象
		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(AppConfig.class);

		System.out.println(applicationContext.getBean(Y.class));

//		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
//		ac.register(AppConfig.class);
//		ac.refresh();
//
//		// Spring 已经初始化完成,X已经在Spring容器中
//		X x = (X) ac.getBean("x");
//		x.sayHello();


	}

}
