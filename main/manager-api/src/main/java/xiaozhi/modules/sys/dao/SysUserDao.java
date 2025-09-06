package xiaozhi.modules.sys.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.vo.ChatCountVO;

/**
 * 系统用户
 */
@Mapper
public interface SysUserDao extends BaseDao<SysUserEntity> {

    /**
     * 获取聊天次数统计
     * 
     * @param date 查询日期 (YYYY-MM-DD)
     * @param minCount 最小聊天次数
     * @return 聊天次数统计列表
     */
    List<ChatCountVO> getChatCount(@Param("date") String date, @Param("minCount") Integer minCount);

}