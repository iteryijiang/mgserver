package cn.jeeweb.web.ebp.shop.mapper;

import cn.jeeweb.web.ebp.shop.entity.TshopInfo;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TshopInfoMapper extends BaseMapper<TshopInfo> {
	
	List<TshopInfo> selectUserList(Pagination page, @Param("ew") Wrapper<TshopInfo> wrapper);
	List<TshopInfo>  findshopInfo();
	TshopInfo selectOne(String userid);
	Map selectSumOne(@Param("map") Map map);
}