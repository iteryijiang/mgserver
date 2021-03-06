package cn.jeeweb.web.ebp.shop.mapper;

import cn.jeeweb.web.ebp.shop.entity.TtaskBase;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TtaskBaseMapper extends BaseMapper<TtaskBase> {
	
	List<TtaskBase> selectTtaskBaseList(Pagination page, @Param("ew") Wrapper<TtaskBase> wrapper);

	List<TtaskBase> selectShopTask(@Param("shopid") String shopid,@Param("count") int count,@Param("createBy") String user,@Param("minute") int minute);
	List<TtaskBase> selectShopTaskNew(@Param("minute") int minute);

//@Param("createby") String createby, @Param("createDate1") String createDate1, @Param("createDate2") String createDate2, @Param("shopname") String shopname,@Param("tTitle")String tTitle,@Param("status") String status
	Map sumNumAndPrice(@Param("map") Map map);

	Map showTaskBaseLoadFinance(@Param("map") Map map);

	Integer sumTtaskBase(@Param("map") Map map);

	Integer sumCanreceivenum(@Param("minute") int minute);

	List<TtaskBase> selectShopList(Pagination page, @Param("ew") Wrapper<TtaskBase> wrapper);

	List<Map> selectFinanceList(@Param("createDate1") String createDate1, @Param("createDate2") String createDate2);

	List<Map> selectWithdrawalMoneyList(@Param("map") Map map);

	List<Map> listFinanceShopReport(@Param("map") Map map);
}