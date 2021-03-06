package cn.jeeweb.web.ebp.finance.mapper;

import cn.jeeweb.web.ebp.finance.entity.TfinanceRecharge;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TfinanceRechargeMapper extends BaseMapper<TfinanceRecharge> {
	
	List<TfinanceRecharge> selectUserList(Pagination page, @Param("ew") Wrapper<TfinanceRecharge> wrapper);
}