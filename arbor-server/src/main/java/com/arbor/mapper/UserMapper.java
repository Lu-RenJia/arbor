package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问层（MyBatis XML）
 *
 * @author gwl
 */
@Mapper
public interface UserMapper {

    User selectByUsername(String username);

    User selectById(Long id);

    IPage<User> selectPage(IPage<User> page);

    int insert(User entity);

    int update(User entity);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateProfile(@Param("id") Long id, @Param("nickname") String nickname, @Param("sex") String sex);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int deleteById(Long id);
}
