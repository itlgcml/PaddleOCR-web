package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 系统用户表 Mapper
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    /**
     * 登录失败计数原子自增（SQL 原子操作，禁止先查后写的 read-modify-write）
     *
     * <p>locked_until 赋值放在 failed_attempts 自增之前：MySQL 的 SET 按从左到右顺序求值，
     * 先自增会使 IF 中的 failed_attempts 读到新值导致阈值判断偏移一位</p>
     *
     * @param id 用户 ID
     * @param maxFailedAttempts 锁定阈值（连续失败次数上限）
     * @param lockMinutes 锁定时长（分钟）
     * @return 影响行数
     */
    @Update("UPDATE sys_user SET locked_until = IF(failed_attempts + 1 >= #{maxFailedAttempts}, "
            + "DATE_ADD(NOW(), INTERVAL #{lockMinutes} MINUTE), locked_until), "
            + "failed_attempts = failed_attempts + 1, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int increaseFailedAttempts(@Param("id") Long id,
                               @Param("maxFailedAttempts") int maxFailedAttempts,
                               @Param("lockMinutes") int lockMinutes);

    /**
     * 登录成功后清零失败计数与锁定时间
     *
     * @param id 用户 ID
     * @return 影响行数
     */
    @Update("UPDATE sys_user SET failed_attempts = 0, locked_until = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int resetLoginFailure(@Param("id") Long id);

    /**
     * 登录成功后写入最近登录时间与 IP
     *
     * @param id 用户 ID
     * @param loginTime 登录时间
     * @param loginIp 登录 IP
     * @return 影响行数
     */
    @Update("UPDATE sys_user SET last_login_time = #{loginTime}, last_login_ip = #{loginIp}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int updateLoginInfo(@Param("id") Long id,
                        @Param("loginTime") LocalDateTime loginTime,
                        @Param("loginIp") String loginIp);
}
