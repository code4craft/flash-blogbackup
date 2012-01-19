package com.flash.blogbackup.model;/** * @author cairne huangyihua@diandian.com * @date Oct 4, 2011 */public enum BlogType {    sina("sina", "新浪博客", "sina.png"), //    netease("netease", "网易博客", "netease.png"), //    baidu("baidu", "百度空间", "baidu.gif"), //    lily("lily", "小百合blog", "lily.gif");    private String value;    private String message;    private String image;    private final String filePath = "resources/";    private BlogType(String value) {        this.value = value;    }    private BlogType(String value, String message, String image) {        this.value = value;        this.message = message;        this.image = image;    }    /**     * @return the value     */    public String getValue() {        return value;    }    public String getImage() {        return filePath + image;    }    /**     * @return the message     */    public String getMessage() {        return message;    }    public String toString() {        return message;    }}