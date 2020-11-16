package com.spring.nya;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 *
 * Spring框架中用到的设计模式:
 * 	工厂设计模式: BeanFactory, ApplicationContext
 * 	代理设计模式: Spring AOP
 * 	单例设计模式: Spring Bean
 * 	模板方法模式: jdbcTemplate, JpaRepository
 * 	包装器设计模式: 连接多个数据库 dataSources
 * 	观察者模式: Spring事件驱动模型
 * 	适配器模式: Spring AoP的增强或通知(Advice)使用到了适配器模式,Spring MVC调用使用HandlerAdapter适配Controller
 *
 * @Description 通用配置类
 * @Author nya
 * @Date 2020/9/27 下午5:02
 **/
@Configuration
@ComponentScan("com.spring.nya")
public class AppConfig {


}
