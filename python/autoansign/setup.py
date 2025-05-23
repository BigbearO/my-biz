from setuptools import setup, find_packages

setup(
    name='airtest_mobile',
    version='0.0.1',
    # 版本号后缀说明：
    # - a1：早期测试版（Alpha 版本）
    # - b1：功能较完整但可能有问题的测试版（Beta 版本）
    # - rc1：接近最终稳定版的发布候选版本（Release Candidate）
    # - dev1：开发中版本（未完成，内部测试）
    # - post1：正式版发布后的补丁版本
    # 示例：'2.3.0a1' 表示 2.3.0 的第 1 个 Alpha 测试版
    author='bigbear',
    author_email='who@cndaqiang.ac.cn',
    description='auto mobile framework using airtest ',
    long_description=open('README.md', encoding='utf-8').read(),  # 从 autoansign/README.md 读取 long description
    long_description_content_type='text/markdown',
    packages=find_packages(),  # 自动查找所有子包
    # 需要在AutoWZRY下面创建__init__.[y]
    package_data={             # 指定需要包含的额外文件
        'airtest_mobile': [
            'assets/*',         # 包括 autoansign/assets 下的所有文件
        ],
    },
    include_package_data=True,  # 自动包含 package_data 中指定的文件
    url='https://github.com/MobileAutoFlow/autoansign',
    install_requires=[
        'airtest-mobileauto>=2.1.2',
        'airtest',
    ],
    entry_points={
        'console_scripts': [
            'autoablesci=autoansign.web_ablesci:main',  # 直接引用脚本文件
            'automuchong=autoansign.web_muchong:main',  # 直接引用脚本文件
            'autourl=autoansign.web_url:main',  # 直接引用脚本文件
            'autoalicloud=autoansign.app_alicloud:main',  # 直接引用脚本文件
        ],
    },
    classifiers=[
        'Development Status :: 5 - Production/Stable',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: MIT License',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.10',
        'Topic :: Software Development :: Libraries',
        'Topic :: Utilities',
    ],
    python_requires='>=3.10',
)
