package cn.jeeweb.web.ebp.shop.controller;

import cn.jeeweb.beetl.tags.dict.Dict;
import cn.jeeweb.beetl.tags.dict.DictUtils;
import cn.jeeweb.common.http.PageResponse;
import cn.jeeweb.common.http.Response;
import cn.jeeweb.common.mvc.annotation.ViewPrefix;
import cn.jeeweb.common.mvc.controller.BaseBeanController;
import cn.jeeweb.common.mybatis.mvc.wrapper.EntityWrapper;
import cn.jeeweb.common.query.annotation.PageableDefaults;
import cn.jeeweb.common.query.data.Condition;
import cn.jeeweb.common.query.data.PropertyPreFilterable;
import cn.jeeweb.common.query.data.Queryable;
import cn.jeeweb.common.query.utils.QueryableConvertUtils;
import cn.jeeweb.common.security.shiro.authz.annotation.RequiresMethodPermissions;
import cn.jeeweb.common.security.shiro.authz.annotation.RequiresPathPermission;
import cn.jeeweb.common.utils.DateUtils;
import cn.jeeweb.common.utils.StringUtils;
import cn.jeeweb.web.aspectj.annotation.Log;
import cn.jeeweb.web.aspectj.enums.LogType;
import cn.jeeweb.web.ebp.buyer.entity.TmyTask;
import cn.jeeweb.web.ebp.buyer.entity.TmyTaskDetail;
import cn.jeeweb.web.ebp.buyer.service.TmyTaskDetailService;
import cn.jeeweb.web.ebp.buyer.service.TmyTaskService;
import cn.jeeweb.web.ebp.finance.service.TfinanceRechargeService;
import cn.jeeweb.web.ebp.shop.entity.TshopBase;
import cn.jeeweb.web.ebp.shop.entity.TshopInfo;
import cn.jeeweb.web.ebp.shop.entity.TtaskBase;
import cn.jeeweb.web.ebp.shop.service.TshopBaseService;
import cn.jeeweb.web.ebp.shop.service.TshopInfoService;
import cn.jeeweb.web.ebp.shop.service.TtaskBaseService;
import cn.jeeweb.web.ebp.shop.spider.JdSpider;
import cn.jeeweb.web.ebp.shop.spider.TsequenceSpider;
import cn.jeeweb.web.ebp.shop.util.TaskUtils;
import cn.jeeweb.web.utils.UserUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializeFilter;
import org.jsoup.nodes.Document;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;


@RestController
@RequestMapping("${jeeweb.admin.url.prefix}/shop/TtaskBase")
@ViewPrefix("ebp/shop")
@RequiresPathPermission("shop:TtaskBase")
@Log(title = "订单管理")
public class TtaskBaseController extends BaseBeanController<TtaskBase> {

    @Autowired
    private TtaskBaseService ttaskBaseService;
    @Autowired
    private TshopInfoService tshopInfoService;
    @Autowired
    private TmyTaskService tmyTaskService;
    @Autowired
    private TmyTaskDetailService tmyTaskDetailService;
    @Autowired
    private TshopBaseService tshopBaseService;

    @GetMapping
    @RequiresMethodPermissions("view")
    public ModelAndView list(Model model, HttpServletRequest request, HttpServletResponse response) {
        TtaskBase tb = new TtaskBase();
        TshopInfo si = tshopInfoService.selectOne(UserUtils.getPrincipal().getId());
        if(si==null){
            si = new TshopInfo();
        }
        model.addAttribute("tb",tb);
        model.addAttribute("si",si);
        model.addAttribute("presentdeposit",Double.parseDouble(DictUtils.getDictValue("一个任务单发布佣金", "tasknum", "2.5")));
        return displayModelAndView("ReleaseTask");
    }

    @GetMapping(value = "TaskDetail")
    @RequiresMethodPermissions("TaskDetail")
    public ModelAndView TaskDetail(Model model, HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mav = displayModelAndView("TaskDetail");
        return mav;
    }

    @GetMapping(value = "listFinance")
    @RequiresMethodPermissions("listFinance")
    public ModelAndView listFinance(Model model, HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mav = displayModelAndView("list_finance");
        return mav;
    }

    @GetMapping(value = "listFinanceCommissions")
    @RequiresMethodPermissions("listFinanceCommissions")
    public ModelAndView listFinanceCommissions(Model model, HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mav = displayModelAndView("list_finance_commissions");
        return mav;
    }
    @GetMapping(value = "listFinanceShopReportHTML")
    @RequiresMethodPermissions("listFinanceShopReportHTML")
    public ModelAndView listFinanceShopReportHTML(Model model, HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mav = displayModelAndView("list_finance_shop_report");
        return mav;
    }
    /**
     * 任务发布，任务状态status：0进行中，1已完成。2结束。
     * */
    @PostMapping("add")
    @Log(logType = LogType.INSERT)
    @RequiresMethodPermissions("add")
    public Response add(@RequestBody TtaskBase ttaskBase,HttpServletRequest request, HttpServletResponse response) {

        try {
            if(StringUtils.isEmpty(ttaskBase.gettUrl())||StringUtils.isEmpty(ttaskBase.getQrcodeurl())){
                return Response.error("订单地址或二维码为空！");
            }
            if(null==ttaskBase.getEffectdate()){
                return Response.error("生效时间为空！");
            }
            ttaskBase.setShopid(UserUtils.getUser().getId());
            TshopInfo si = tshopInfoService.selectOne(ttaskBase.getShopid());
            //      ttaskBase.setTaskno((new Date().getTime())+""+(new Random().nextInt(9999)+1));
            ttaskBase.setTaskno(TsequenceSpider.getShopNo());
            ttaskBase.setCanreceivenum(ttaskBase.getTasknum());
            if(ttaskBase.gettPrice()!=null&&ttaskBase.gettNum()!=null){
                ttaskBase.setTotalprice(ttaskBase.getActualprice().multiply(new BigDecimal(ttaskBase.getTasknum())));
            }
            ttaskBase.setStatus("0");
            Double countSum = Double.parseDouble(DictUtils.getDictValue("一个任务单发布佣金", "tasknum", "2.5"));
            BigDecimal price = ttaskBase.getTotalprice().add(new BigDecimal(ttaskBase.getTasknum()*countSum));
            if(si.getTotaldeposit()==null) {
                return Response.error("发布失败，您无押金，请充值！");
            }else if(si.getTotaldeposit().compareTo(price)<0){
                return Response.error("发布失败，您押金不够，请充值！");
            }
            si.setTotaldeposit(si.getTotaldeposit().subtract(price));
            if(si.getTaskdeposit()==null){
                si.setTaskdeposit(price);
            }else {
                si.setTaskdeposit(si.getTaskdeposit().add(price));
            }
            ttaskBase.setTaskdeposit(price);
            ttaskBase.setPresentdeposit(new BigDecimal(countSum));
            ttaskBaseService.addTask(ttaskBase,si);
        }catch (Exception e){
            e.printStackTrace();
            return Response.error("发布失败！");
        }
        //保存之后
        return Response.ok("发布成功！");
    }

    @PostMapping("update")
    @Log(logType = LogType.UPDATE)
    @RequiresMethodPermissions("update")
    public Response update(@RequestBody TtaskBase entity, BindingResult result,
                        HttpServletRequest request, HttpServletResponse response) {
        try {
            TtaskBase tb = ttaskBaseService.selectById(entity.getId());
            BeanUtils.copyProperties(tb,entity,"tType,tUrl,tTitle,tPrice,tNum,totalPrice,searchPrice");
            ttaskBaseService.insertOrUpdate(tb);
        }catch (Exception e){
            e.printStackTrace();
        }
        return Response.ok("更新成功");
    }

    @PostMapping("delete")
    @Log(logType = LogType.DELETE)
    @RequiresMethodPermissions("delete")
    public Response delete(@PathVariable("id") String id) {
        ttaskBaseService.deleteById(id);
        return Response.ok("删除成功");
    }

    @GetMapping(value = "taskList")
    @RequiresMethodPermissions("taskList")
    public ModelAndView myList(Model model, HttpServletRequest request, HttpServletResponse response) {
//        String userid = UserUtils.getPrincipal().getId();
//        Date date = new Date();
//        Calendar calendar = new GregorianCalendar();
//        calendar.add(calendar.DATE,1);
//        Date date2 = calendar.getTime();
//        Map map = sumNumAndPrice(userid,DateUtils.formatDate(date,"yyyy-MM-dd"),DateUtils.formatDate(date2,"yyyy-MM-dd"));
//        model.addAttribute("map",map);

        ModelAndView mav = displayModelAndView("list");
        return mav;
    }

    public Map sumNumAndPrice(Map m){
        Map map = ttaskBaseService.sumNumAndPrice(m);
        if(map==null){
            map = new HashMap();
            map.put("sumActualprice",0);
            map.put("sumFinishPrice",0);
            map.put("sumOrderPrice",0);
            map.put("sumDeliveryPrice",0);
            map.put("sumtasknum",0);
            map.put("sumcanreceivenum",0);
            map.put("sumunanswerednum",0);
            map.put("sumreceivingnum",0);
            map.put("sumordernum",0);
            map.put("sumdeliverynum",0);
            map.put("sumfinishnum",0);
        }
        return map;
    }
    /**
     * 根据页码和每页记录数，以及查询条件动态加载数据
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestMapping(value = "ajaxList", method = { RequestMethod.GET, RequestMethod.POST })
    @PageableDefaults(sort = "taskno=desc")
    @Log(logType = LogType.SELECT)
    @RequiresMethodPermissions("list")
    public void ajaxList(Queryable queryable, PropertyPreFilterable propertyPreFilterable, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        EntityWrapper<TtaskBase> entityWrapper = new EntityWrapper<>(entityClass);
        propertyPreFilterable.addQueryProperty("id");
        String userid = UserUtils.getPrincipal().getId();
        if (!StringUtils.isEmpty(userid)&&!"admin".equals(UserUtils.getUser().getUsername())) {
            entityWrapper.eq("t.create_by", userid);
        }
        entityWrapper.setTableAlias("t");
        if(queryable.getCondition()!=null){
            Condition.Filter filter = queryable.getCondition().getFilterFor("effectdate");
            if(filter!=null){
                queryable.getCondition().remove(filter);
                queryable.getCondition().and(Condition.Operator.between,"effectdate",TaskUtils.whereDate(filter));
            }

            Condition.Filter Filter_name = queryable.getCondition().getFilterFor("shopname");
            if(Filter_name!=null){
                queryable.getCondition().remove(Filter_name);
                entityWrapper.like("s.shopname",Filter_name.getValue().toString());
            }

        }
        // 预处理
        QueryableConvertUtils.convertQueryValueToEntityValue(queryable, entityClass);
        SerializeFilter filter = propertyPreFilterable.constructFilter(entityClass);
        PageResponse<TtaskBase> pagejson = new PageResponse<>(ttaskBaseService.list(queryable,entityWrapper));
        String content = JSON.toJSONString(pagejson, filter);
        StringUtils.printJson(response,content);
    }

    @RequestMapping(value = "financeList", method = { RequestMethod.GET, RequestMethod.POST })
    @Log(logType = LogType.SELECT)
    public void financeList(Queryable queryable, PropertyPreFilterable propertyPreFilterable, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        EntityWrapper<TtaskBase> entityWrapper = new EntityWrapper<>(entityClass);
        propertyPreFilterable.addQueryProperty("id");
        String[] creates = TaskUtils.whereNewDate("","");
        if(queryable.getCondition()!=null){
            Condition.Filter filter = queryable.getCondition().getFilterFor("createDate");
            creates = TaskUtils.whereDate(filter);
        }
        List<Map> list = ttaskBaseService.selectFinanceList(creates[0],creates[1]);
        BigDecimal sumPays = new BigDecimal(0.0);
        BigDecimal sumOrderPrice = new BigDecimal(0.0);
        BigDecimal sumDeliveryPrice = new BigDecimal(0.0);
        for (Map map:list) {
            sumPays = sumPays.add(new BigDecimal(map.get("sumPays").toString()));
            sumOrderPrice = sumOrderPrice.add(new BigDecimal(map.get("sumOrderPrice").toString()));
            sumDeliveryPrice = sumDeliveryPrice.add(new BigDecimal(map.get("sumDeliveryPrice").toString()));
        }
        Map map = new HashMap();
        map.put("createDate","");
        map.put("buyerName","合计");
        map.put("sumPays",sumPays);
        map.put("sumOrderPrice",sumOrderPrice);
        map.put("sumDeliveryPrice",sumDeliveryPrice);
        list.add(map);
        String content = JSON.toJSONString(list);
        StringUtils.printJson(response,content);
    }

    @RequestMapping(value = "withdrawalMoneyList", method = { RequestMethod.GET, RequestMethod.POST })
    @Log(logType = LogType.SELECT)
    public void withdrawalMoneyList(Queryable queryable, PropertyPreFilterable propertyPreFilterable, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        EntityWrapper<TtaskBase> entityWrapper = new EntityWrapper<>(entityClass);
        propertyPreFilterable.addQueryProperty("id");

        Double multiple = 2.0;
        try{
            multiple = Double.parseDouble(DictUtils.getDictValue("一个任务单佣金","tasknum",multiple+""));
        }catch (Exception e){

        }
        String userid = "";
        if (!"admin".equals(UserUtils.getUser().getUsername())) {
            userid = UserUtils.getPrincipal().getId();
        }
        String[] creates = TaskUtils.whereNewDate("","");
        if(queryable.getCondition()!=null){
            Condition.Filter filter = queryable.getCondition().getFilterFor("countCreateDate");
            creates = TaskUtils.whereDate(filter);
        }
        Map parmas_map = new HashMap();
        parmas_map.put("createDate1",creates[0]);
        parmas_map.put("createDate2",creates[1]);
        parmas_map.put("multiple",multiple);
        parmas_map.put("userid",userid);
        List<Map> list = ttaskBaseService.selectWithdrawalMoneyList(parmas_map);
        Long sumCount = 0l;
        Long sumConfirm = 0l;
        BigDecimal sumWithdrawalmoney = new BigDecimal(0.0);
        for (Map map:list) {
            sumCount += Long.parseLong(map.get("sumCount").toString());
            sumConfirm += Long.parseLong(map.get("sumConfirm").toString());
            sumWithdrawalmoney = sumWithdrawalmoney.add(new BigDecimal(map.get("sumWithdrawalmoney").toString()));
        }
        Map map = new HashMap();
        map.put("countCreateDate","");
        map.put("buyerName","合计");
        map.put("sumCount",sumCount);
        map.put("sumConfirm",sumConfirm);
        map.put("sumWithdrawalmoney",sumWithdrawalmoney);
        list.add(map);
        String content = JSON.toJSONString(list);
        StringUtils.printJson(response,content);
    }

    @RequestMapping(value = "listFinanceShopReport", method = { RequestMethod.GET, RequestMethod.POST })
    @Log(logType = LogType.SELECT)
    public void listFinanceShopReport(Queryable queryable, PropertyPreFilterable propertyPreFilterable, HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        EntityWrapper<TtaskBase> entityWrapper = new EntityWrapper<>(entityClass);
        propertyPreFilterable.addQueryProperty("id");

        Date date = new Date();
        String shopname = "";
        String basename = "";
        String loginname = "";
        String[] creates = TaskUtils.whereNewDate("","");
        if(queryable.getCondition()!=null){
            Condition.Filter filter = queryable.getCondition().getFilterFor("counteffectdate");
            creates = TaskUtils.whereDate(filter);
        }
        if(queryable.getCondition()!=null){
            Condition.Filter shopnameFilter = queryable.getCondition().getFilterFor("shopname");
            if(shopnameFilter!=null){
                shopname = "%"+(String)shopnameFilter.getValue()+"%";
            }
            Condition.Filter basenameFilter = queryable.getCondition().getFilterFor("basename");
            if(basenameFilter!=null){
                basename = "%"+(String)basenameFilter.getValue()+"%";
            }
            Condition.Filter loginnameFilter = queryable.getCondition().getFilterFor("loginname");
            if(loginnameFilter!=null){
                loginname = "%"+(String)loginnameFilter.getValue()+"%";
            }
        }
        Map par_map = new HashMap();
        par_map.put("createDate1",creates[0]);
        par_map.put("createDate2",creates[1]);
        par_map.put("shopname",shopname);
        par_map.put("basename",basename);
        par_map.put("loginname",loginname);
        String userid = UserUtils.getPrincipal().getId();
        if (!StringUtils.isEmpty(userid)&&!"admin".equals(UserUtils.getUser().getUsername())) {
            par_map.put("shopid",userid);
        }
        List<Map> list = ttaskBaseService.listFinanceShopReport(par_map);
        Double sumActualprice = 0.0;
        Double sumOrderPrice = 0.0;
        Double sumDeliveryPrice = 0.0;
        Double sumFinishPrice = 0.0;
        Long sumCount = 0l;
        Long sumreceivingnum = 0l;
        Long sumordernum = 0l;
        Long sumdeliverynum = 0l;
        Long sumfinishnum = 0l;
        for (Map map:list) {
            sumActualprice += Double.parseDouble((map.get("sumActualprice")==null?"0":map.get("sumActualprice")).toString());
            sumOrderPrice += Double.parseDouble((map.get("sumOrderPrice")==null?"0":map.get("sumOrderPrice")).toString());
            sumDeliveryPrice += Double.parseDouble((map.get("sumDeliveryPrice")==null?"0":map.get("sumDeliveryPrice")).toString());
            sumFinishPrice += Double.parseDouble((map.get("sumFinishPrice")==null?"0":map.get("sumFinishPrice")).toString());
            sumCount += Long.parseLong((map.get("sumCount")==null?"0":map.get("sumCount")).toString());
            sumreceivingnum += Long.parseLong((map.get("sumreceivingnum")==null?"0":map.get("sumreceivingnum")).toString());
            sumordernum += Long.parseLong((map.get("sumordernum")==null?"0":map.get("sumordernum")).toString());
            sumdeliverynum += Long.parseLong((map.get("sumdeliverynum")==null?"0":map.get("sumdeliverynum")).toString());
            sumfinishnum += Long.parseLong((map.get("sumfinishnum")==null?"0":map.get("sumfinishnum")).toString());
        }
        Map map = new HashMap();
        map.put("countCreateDate","");
        map.put("shopname","合计");
        map.put("sumActualprice",sumActualprice);
        map.put("sumOrderPrice",sumOrderPrice);
        map.put("sumDeliveryPrice",sumDeliveryPrice);
        map.put("sumFinishPrice",sumFinishPrice);
        map.put("sumCount",sumCount);
        map.put("sumreceivingnum",sumreceivingnum);
        map.put("sumordernum",sumordernum);
        map.put("sumdeliverynum",sumdeliverynum);
        map.put("sumfinishnum",sumfinishnum);
        list.add(map);
        String content = JSON.toJSONString(list);
        StringUtils.printJson(response,content);
    }
    /**
     * 根据页码和每页记录数，以及查询条件动态加载数据
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestMapping(value = "myTaskList", method = { RequestMethod.GET, RequestMethod.POST })
    @Log(logType = LogType.SELECT)
    @RequiresMethodPermissions("myTaskList")
    public void myTaskList(Queryable queryable, PropertyPreFilterable propertyPreFilterable, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        List listBase = new ArrayList();
        String user = UserUtils.getPrincipal().getId();
        try {
            //获得商户表
            List<TshopInfo> listShop = tshopInfoService.findshopInfo();
            int count = 7;
            int sum = 0;
            Map<String, Integer> map = new HashMap();
            for (TshopInfo si : listShop) {
                Random rand = new Random();
                int a = rand.nextInt(3) + 1;
                sum += a;
                if (sum > 7) {
                    a = a - (sum - 7);
                }
                map.put(si.getUserid(), a);
                if (sum >= 7) {
                    break;
                }
            }
            //根据每个商户数量返回订单数
            Iterator<Map.Entry<String, Integer>> entries = map.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, Integer> entry = entries.next();
                System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
                List<TtaskBase> list = ttaskBaseService.selectShopTask(entry.getKey(), entry.getValue(),user,10);
                listBase.addAll(list);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        String content = JSON.toJSONString(listBase);
        StringUtils.printJson(response,content);
    }

    @RequestMapping(value = "showTitle", method = { RequestMethod.GET, RequestMethod.POST })
    @RequiresMethodPermissions("showTitle")
    public void showTitle(@RequestBody JSONObject jsonObject, HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        Map map = new HashMap();
        try {
            String turl = jsonObject.getString("turl");
            Document document = JdSpider.getDocumentUrl(turl);
            if(document!=null){
                String goodsrc = JdSpider.getGoodImgByurl(document);//获取图片
                String ttitle = JdSpider.getGoodTitleByurl(document);//获取商品标题
                String brand = JdSpider.getGoodBrandByurl(document);//商品品牌
                String storename = JdSpider.getGoodStorenameByurl(document);//商品店铺
                String article = JdSpider.getGoodarticleByurl(document);//商品货号

                String goodis = JdSpider.getGoodId_ByURL(turl);//获取商品ID
                String result = JdSpider.getGoodInfos(goodis);//获取商品详细信息
                String good_price  = JdSpider.getGoodPrice_ByResult(result);//获取商品价格
                String spec1 = JdSpider.getGoodSpec1ByTitle(ttitle);//商品颜色
                String spec2 = JdSpider.getGoodSpec2ByTitle(ttitle);//商品规格
                map.put("goodsrc",goodsrc);
                map.put("ttitle",ttitle);
                map.put("goodprice",good_price);
                map.put("spec1",spec1);
                map.put("spec2",spec2);
                map.put("brand",brand);
                map.put("storename",storename);
                map.put("article",article);
            }

        }catch (Exception e){
            e.printStackTrace();
        }
        String content = JSON.toJSONString(map);
        StringUtils.printJson(response,content);
    }

    @GetMapping(value = "listPool")
    @RequiresMethodPermissions("listPool")
    public ModelAndView listPool(Model model, HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mav = displayModelAndView("listPool");
        return mav;
    }

    @GetMapping(value = "{id}/listPoolGet")
    public ModelAndView listPoolGet(@PathVariable("id") String id, Model model,HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        TtaskBase tb = ttaskBaseService.selectById(id);
        TshopInfo tsi = tshopInfoService.selectOne(tb.getShopid());
        TshopBase tsb = tshopBaseService.selectById(tb.getStorename());
        String display = "none";
        if ("admin".equals(UserUtils.getUser().getUsername())) {
            display = "block";
        }
        model.addAttribute("display",display);
        model.addAttribute("tb",tb);
        model.addAttribute("tsi",tsi);
        model.addAttribute("tsb",tsb);
        //进行中，完成数
        int taskstatus0=0,taskstatus1=0;
        int taskstate2=0,taskstate4=0;

        List<Map> list = tmyTaskDetailService.groupBytaskstatus(tb.getId());
        for (int i=0;i<list.size();i++){
            Map map = list.get(i);
            if("0".equals(map.get("taskstatus").toString())){
                taskstatus0 = Integer.parseInt(map.get("counts").toString());
            }else if("1".equals(map.get("taskstatus").toString())){
                taskstatus1 = Integer.parseInt(map.get("counts").toString());
            }
        }
        List<Map> listState = tmyTaskDetailService.groupBytaskstate(tb.getId());
        for (int i=0;i<listState.size();i++){
            Map map = listState.get(i);
            if("2".equals(map.get("taskstate").toString())){
                taskstate2 = Integer.parseInt(map.get("counts").toString());
            }else if("4".equals(map.get("taskstate").toString())){
                taskstate4 = Integer.parseInt(map.get("counts").toString());
            }
        }
        model.addAttribute("taskstatus0",taskstatus0);
        model.addAttribute("taskstatus1",taskstatus1);
        model.addAttribute("taskstate2",taskstate2);
        model.addAttribute("taskstate4",taskstate4);
        model.addAttribute("createDateFormat",DateUtils.formatDate(tb.getCreateDate(),"yyyy-MM-dd"));
        model.addAttribute("effectdateFormat",DateUtils.formatDate(tb.getEffectdate(),"yyyy-MM-dd"));
        ModelAndView mav = displayModelAndView("TaskDetailForSeller");
        return mav;
    }

//    public static void main(String[] args){
//        int count = 7;
//        int sum = 0;
//        Map<String,Integer> map = new HashMap();
//        for (int i=0;i<10;i++) {
//            Random rand = new Random();
//            int a = rand.nextInt(3) + 1;
//            sum +=a;
//            if(sum>7){
//                a = a-(sum-7);
//            }
//            map.put("A"+i,a);
//            if(sum>=7){
//                break;
//            }
//        }
//        Iterator<Map.Entry<String,Integer>> entries = map.entrySet().iterator();
//        while (entries.hasNext()) {
//            Map.Entry<String,Integer> entry = entries.next();
//            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
//        }
//    }


    @GetMapping(value = "{diccode}/getDictList")
    public void getDictList(@PathVariable("diccode") String diccode, HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        List<Dict> list = new ArrayList<Dict>();
        try {
            list = DictUtils.getDictList(diccode);
        }catch (Exception e){
            e.printStackTrace();
        }
        String content = JSON.toJSONString(list);
        StringUtils.printJson(response,content);
    }

    /**
     *领取任务，我的任务state：状态0：进行中，1已完成
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestMapping(value = "myTaskCreate", method = { RequestMethod.GET, RequestMethod.POST })
    @Log(logType = LogType.INSERT)
    @RequiresMethodPermissions("myTaskCreate")
    public Response myTaskCreate(HttpServletRequest request,HttpServletResponse response) throws Exception {
        try {
            //如果上一任务未完成，则不能领取任务
            Map map = new HashMap();
            map.put("userid",UserUtils.getPrincipal().getId());
            map.put("taskstate","1");
            if(tmyTaskDetailService.sumMyTask(map)>0){
                return Response.ok("您有未确定下单任务，无法领取！");
            }

            boolean bool = ttaskBaseService.createMyTask();
            if(!bool){
                return Response.ok("无可领取任务！");
            }
        }catch (Exception e){
            e.printStackTrace();
            return Response.ok("领取失败！");
        }
        return Response.ok("领取成功！");

//        ttaskBaseService.myTaskMap();
//        List<TtaskBase> list_tb = new ArrayList<TtaskBase>();
//        TtaskBase t1 = new TtaskBase();
//        t1.settTitle("A");
//        t1.setCanreceivenum(5l);
//        t1.setCanreceivenums(t1.getCanreceivenum());
//        list_tb.add(t1);
//
//        TtaskBase t2 = new TtaskBase();
//        t2.settTitle("B");
//        t2.setCanreceivenum(4l);
//        t2.setCanreceivenums(t2.getCanreceivenum());
//        list_tb.add(t2);
//
//        TtaskBase t3 = new TtaskBase();
//        t3.settTitle("D");
//        t3.setCanreceivenum(4l);
//        t3.setCanreceivenums(t3.getCanreceivenum());
//        list_tb.add(t3);
//
//        TtaskBase t4 = new TtaskBase();
//        t4.settTitle("C");
//        t4.setCanreceivenum(3l);
//        t4.setCanreceivenums(t4.getCanreceivenum());
//        list_tb.add(t4);
//
//        List<TtaskBase> list_oldtb = new ArrayList<TtaskBase>();
//        list_oldtb.addAll(list_tb);
//        Map a = ttaskBaseService.shopMap(list_tb,1,2,new HashMap(),1);
//        Iterator<Map.Entry<Integer, List<TtaskBase>>> a1 = a.entrySet().iterator();
//
//        while (a1.hasNext()) {
//            Map.Entry<Integer, List<TtaskBase>> aentry = a1.next();
//            System.out.println(aentry.getKey());
//            for(int i=0;i<aentry.getValue().size();i++){
//                TtaskBase bb = aentry.getValue().get(i);
//                System.out.println(">>>>>>>>>>>>>>>>>>>"+bb.gettTitle()+"_______________"+bb.getCanreceivenum());
//            }
//        }
    }

    @GetMapping(value = "{id}/uploadQrcode")
    public ModelAndView uploadQrcode(@PathVariable("id") String id, Model model,HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        TtaskBase tb = ttaskBaseService.selectById(id);
        model.addAttribute("data",tb);
        return displayModelAndView("avatar");
    }
    @PostMapping(value = "{id}/uploadQrcode")
    @Log(logType = LogType.OTHER,title = "上传二维码")
    public Response uploadQrcode(TtaskBase ttaskBase, HttpServletRequest request, HttpServletResponse response) {
        try {
            //ttaskBase.getId() 获取两个同样的id
            String id = ttaskBase.getId().split(",")[0];
            TtaskBase oldtaskbase = ttaskBaseService.selectById(id);
            oldtaskbase.setQrcodeurl(ttaskBase.getQrcodeurl());
            ttaskBaseService.insertOrUpdate(oldtaskbase);
            String currentUserId = UserUtils.getUser().getId();

        } catch (Exception e) {
            e.printStackTrace();
            Response.error("二维码修改失败");
        }
        return Response.ok("二维码修改成功");
    }

    @GetMapping("{id}/{status}/upStatus")
    @Log(logType = LogType.UPDATE)
    @RequiresMethodPermissions("upStatus")
    public void upTaskState(@PathVariable("id") String id,@PathVariable("status") String status, HttpServletRequest request,
                            HttpServletResponse response) {
        TtaskBase tb = ttaskBaseService.selectById(id);
        TshopInfo si = tshopInfoService.selectOne(tb.getShopid());
        tb.setStatus(status);
        if(tb.getPresentdeposit()!=null){
            BigDecimal price = tb.getPresentdeposit().multiply(new BigDecimal(tb.getCanreceivenum())).add(tb.getActualprice().multiply(new BigDecimal(tb.getCanreceivenum())));
            si.setTotaldeposit(si.getTotaldeposit().add(price));
            si.setTaskdeposit(si.getTaskdeposit().subtract(price));
        }
        ttaskBaseService.upTask(tb,si,TfinanceRechargeService.rechargetype_4);
    }

    @GetMapping(value = "{id}/myAgainList")
    public ModelAndView myAgainList(@PathVariable("id") String id, Model model,HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        TtaskBase tb = ttaskBaseService.selectById(id);
        TshopInfo si = tshopInfoService.selectOne(UserUtils.getPrincipal().getId());
        if(si==null){
            si = new TshopInfo();
        }
        model.addAttribute("tb",tb);
        model.addAttribute("si",si);
        model.addAttribute("presentdeposit",Double.parseDouble(DictUtils.getDictValue("一个任务单发布佣金", "tasknum", "2.5")));
        ModelAndView mav = displayModelAndView("ReleaseTask");
        return mav;
    }

    @RequestMapping(value = "showTaskBaseLoad", method = { RequestMethod.GET, RequestMethod.POST })
    public void showTaskBaseLoad(@RequestBody JSONObject jsonObject, HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        Map map = new HashMap();
        try {
            String userid = "";
            if (!"admin".equals(UserUtils.getUser().getUsername())) {
                userid = UserUtils.getPrincipal().getId();
            }
            String create1 = jsonObject.getString("create1");
            String create2 = jsonObject.getString("create2");
            String shopname = jsonObject.getString("shopname");
            String tTitle = jsonObject.getString("tTitle");
            String status = jsonObject.getString("status");
            String article = jsonObject.getString("article");

            String[] creates = TaskUtils.whereNewDate(create1,create2);

            Map m = new HashMap();
            m.put("userid",userid);
            m.put("create1",creates[0]);
            m.put("create2",creates[1]);
            m.put("shopname",(StringUtils.isNotEmpty(shopname)?"%"+shopname+"%":null));
            m.put("tTitle",(StringUtils.isNotEmpty(tTitle)?"%"+tTitle+"%":null));
            m.put("status",status);
            m.put("article",(StringUtils.isNotEmpty(article)?"%"+article+"%":null));
            map = sumNumAndPrice(m);
        }catch (Exception e){
            e.printStackTrace();
        }
        String content = JSON.toJSONString(map);
        StringUtils.printJson(response,content);
    }

    /*
    * 商户统计方法修改
    */
    @RequestMapping(value = "showTaskBaseLoadFinance", method = { RequestMethod.GET, RequestMethod.POST })
    public void showTaskBaseLoadFinance(@RequestBody JSONObject jsonObject, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        Map map = new HashMap();
        try {
            String userid = "";
            if (!"admin".equals(UserUtils.getUser().getUsername())) {
                userid = UserUtils.getPrincipal().getId();
            }
            String create1 = jsonObject.getString("create1");
            String create2 = jsonObject.getString("create2");
            String loginname = jsonObject.getString("loginname");
            String shopname = jsonObject.getString("shopname");
            String status = jsonObject.getString("status");

            String[] creates = TaskUtils.whereNewDate(create1,create2);

            Map m = new HashMap();
            m.put("userid",userid);
            m.put("create1",creates[0]);
            m.put("create2",creates[1]);
            m.put("status",status);
            m.put("loginname",(StringUtils.isNotEmpty(loginname)?"%"+loginname+"%":null));
            m.put("shopname",(StringUtils.isNotEmpty(shopname)?"%"+shopname+"%":null));
            map = ttaskBaseService.showTaskBaseLoadFinance(m);
            if(map==null){
                map = new HashMap();
                map.put("sumActualprice",0);
                map.put("sumFinishPrice",0);
                map.put("sumOrderPrice",0);
                map.put("sumDeliveryPrice",0);
                map.put("sumtasknum",0);
                map.put("sumcanreceivenum",0);
                map.put("sumunanswerednum",0);
                map.put("sumreceivingnum",0);
                map.put("sumordernum",0);
                map.put("sumdeliverynum",0);
                map.put("sumfinishnum",0);
            }
            //获取商户余额，冻结金额等信息
            Integer i = ttaskBaseService.sumTtaskBase(m);
            map.put("sumTaskBase",i);
            Map shopInfoM =  tshopInfoService.selectSumOne(m);
            if(shopInfoM!=null) {
                map.put("sumTotaldeposit",shopInfoM.get("sumTotaldeposit"));
                map.put("sumTaskdeposit",shopInfoM.get("sumTaskdeposit"));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        String content = JSON.toJSONString(map);
        StringUtils.printJson(response,content);
    }
}