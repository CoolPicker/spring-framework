package org.springframework.nya.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Description 测试循环依赖Y
 * @Author nya
 * @Date 2020/9/27 下午5:00
 **/
@Component
public class Y {

	@Autowired
	private X x;

	public Y() {
		System.out.println("Y created");
	}

}
