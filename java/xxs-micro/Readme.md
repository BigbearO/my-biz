1 这个项目 作为java 通用架构 公共依赖构建，无论是 spring 还是quarkus
项目名不带quarkus的，就是spring依赖


2 之后再把study ，xxs-common 里面那些迁移过来

3 问题：现在想让微服务继承bom,但是bom 有俩，mirco 不可能继承 其中一个
 那就只能像pig 一样，bom 包含其他common 模块，再被mirco 引入
 其他模块不能依赖,继承此bom
 但是好像能嵌套,把common-core弄进去

4 或者可以， bom不被micro引入,只引入micro下的common ，然后作为parent，
其他业务项目都继承这个

分开分开，
 最终方案；
1 spring,quarkus 公共依赖在micro
2 再定义父子项目
   xxs-common-spring  -- todo spring 那些将就ruoyi-plus用 之后再迁移公共模块
   xxs-common-quarkus
   这俩定义为 pig 和 ruoyi 的最顶级福利项目
   


