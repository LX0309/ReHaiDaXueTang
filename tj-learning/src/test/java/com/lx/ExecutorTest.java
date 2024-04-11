package com.lx;


import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorTest {

    public static void main(String[] args) {

        //  阿里规约  不能使用    防止OOM内存溢出
//        Executors.newFixedThreadPool(1);//创建固定线程数的线程池
//        Executors.newSingleThreadExecutor();//创建单线程的线程池
//        Executors.newCachedThreadPool();//缓存线程池
//        Executors.newScheduledThreadPool(1);//创建可以延迟运行的线程池

        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(
                3,
                5,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(10));
        //建议1：   任务是CPU运算  推荐核心线程为CPU核数
        //建议2：   任务是属于IO的  推荐核心线程为CPU核数两倍
    }
}
