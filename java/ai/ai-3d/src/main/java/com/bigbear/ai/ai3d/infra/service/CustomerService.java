package com.bigbear.ai.ai3d.infra.service;

import com.bigbear.ai.ai3d.domain.entity.Customer;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final List<Customer> customers = new ArrayList<>();

    @Tool(name = "get_customers", description = "Get a list of customers from the CRM system")
    public List<Customer> getCustomers() {
        return customers;
    }

    @Tool(name = "get_customer_by_name", description = "Get a single customer from the CRM system by name")
    public Customer getCustomerByName(String name) {
        return customers.stream()
            .filter(customer -> customer.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    @PostConstruct
    public void init() {
        customers.addAll(List.of(
            new Customer("1", "张三", "zhangsan@example.com", "13800138001", "阿里巴巴"),
            new Customer("2", "李四", "lisi@example.com", "13800138002", "腾讯科技"),
            new Customer("3", "王五", "wangwu@example.com", "13800138003", "字节跳动"),
            new Customer("4", "赵六", "zhaoliu@example.com", "13800138004", "华为技术"),
            new Customer("5", "钱七", "qianqi@example.com", "13800138005", "百度在线")
        ));
    }

}
