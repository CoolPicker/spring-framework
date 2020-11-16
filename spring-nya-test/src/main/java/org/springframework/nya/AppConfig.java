package org.springframework.nya;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @Description 通用配置类
 * @Author nya
 * @Date 2020/9/27 下午5:02
 **/
@Configuration
@ComponentScan({"org.springframework.nya.service","org.springframework.nya.test"})
public class AppConfig {


}
