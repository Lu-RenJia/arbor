package com.arbor.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.arbor.entity.AppMarket;
import com.arbor.entity.AppMarketApp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMarketMapper {
    IPage<AppMarket> selectPage(IPage<AppMarket> page, @Param("name") String name);
    IPage<AppMarket> selectPageByUserId(IPage<AppMarket> page, @Param("userId") Long userId, @Param("name") String name);
    AppMarket selectById(Long id);
    int insert(AppMarket entity);
    int update(AppMarket entity);
    int deleteById(Long id);

    // 人员
    List<Long> selectUserIdsByMarketId(Long marketId);
    int insertUsers(@Param("marketId") Long marketId, @Param("userIds") List<Long> userIds);
    int deleteUsersByMarketId(Long marketId);

    // 共享应用
    List<com.arbor.vo.AppMarketAppVO> selectAppsByMarketId(@Param("marketId") Long marketId);
    int insertApp(AppMarketApp entity);
    int deleteAppByMarketAndApp(@Param("marketId") Long marketId, @Param("appId") Long appId);
    int countAppByMarketAndApp(@Param("marketId") Long marketId, @Param("appId") Long appId);

    // 用户可见的广场（用户被分配到的广场）
    List<AppMarket> selectByUserId(Long userId);

    // 检查用户是否被分配到指定广场
    int isUserInMarket(@Param("marketId") Long marketId, @Param("userId") Long userId);

    // 查询 app 被共享到了哪些广场
    List<Long> selectMarketIdsByAppId(Long appId);

    // 查询某应用在某广场的共享归属人（取消共享的授权校验用；同 market+app 的多环境行归属人一致）
    Long selectSharedBy(@Param("marketId") Long marketId, @Param("appId") Long appId);

    // 查询某应用被共享到的所有广场明细（含广场名/环境/共享人），我的应用页展示与移除用
    List<com.arbor.vo.AppShareVO> selectSharesByAppId(@Param("appId") Long appId);

    // 单条 JOIN 验证：用户属于广场 AND 应用共享到该广场
    int isUserAndAppInMarket(@Param("marketId") Long marketId, @Param("userId") Long userId, @Param("appId") Long appId);
}
