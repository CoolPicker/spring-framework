package com.spring.nya.test;

import com.spring.nya.service.Z;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;

/**
 * @Description TODO
 * @Author nya
 * @Date 2020/11/3 下午2:11
 **/
public class CommandManager implements ApplicationContextAware {
	private ApplicationContext applicationContext;

	public Object process(Map commandState) {
		Z z = createZ();
		return null;
	}

	protected Z createZ() {
		return this.applicationContext.getBean("z",Z.class);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
