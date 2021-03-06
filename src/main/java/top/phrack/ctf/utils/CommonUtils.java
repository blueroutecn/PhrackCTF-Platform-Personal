/**

 * 

 */
package top.phrack.ctf.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.subject.Subject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.web.servlet.ModelAndView;

import top.phrack.ctf.comparators.CompareScore;
import top.phrack.ctf.models.services.BannedIpServices;
import top.phrack.ctf.models.services.IPlogServices;
import top.phrack.ctf.models.services.SubmissionServices;
import top.phrack.ctf.models.services.UserServices;
import top.phrack.ctf.pojo.BannedIps;
import top.phrack.ctf.pojo.IpLogs;
import top.phrack.ctf.pojo.Users;
import top.phrack.ctf.pojo.RanklistObj;
import top.phrack.ctf.pojo.Submissions;

/**

 * 通用操作放在这里

 *

 * @author Jarvis

 * @date 2016年4月7日

 */
public class CommonUtils {
	
	private static String flag_salt = "OtN0onrc1BWPfsHpczuWiEYZbRDzvRAOZLYySuVeWwd3PQ0Lj0PiGOjVBP9ZU4ab";
	/**

	 * 

	 * 获取当前控制器名称

	 */
	public static void setControllerName(final HttpServletRequest request,final ModelAndView mv){
		String servpath = request.getServletPath().substring(1);
		mv.addObject("ctrlname",servpath);
	} 
	
	private final static Whitelist user_content_filter = Whitelist.relaxed();  
	static {  
	    user_content_filter.addTags("embed","object","param","span","div");  
	    user_content_filter.addAttributes(":all", "style", "class", "id", "name");  
	    user_content_filter.addAttributes("object", "width", "height","classid","codebase");      
	    user_content_filter.addAttributes("param", "name", "value");  
	    user_content_filter.addAttributes("embed", "src","quality","width","height","allowFullScreen","allowScriptAccess","flashvars","name","type","pluginspage");  
	}  
	  
	/** 

	 * 对用户输入内容进行过滤 

	 * @param html 

	 * @return 

	 */  
	public static String filterUserInputContent(String html) {  
	    if(StringUtils.isBlank(html)) return "";  
	    return Jsoup.clean(html, user_content_filter);  
	    //return filterScriptAndStyle(html);  


	}  
	
	
	/**

	 *  统计用户使用信息

	 */
	public static void storeUserIpUsageInfo(HttpServletRequest request,UserServices userServices,IPlogServices ipLogServices,String username){
		String userip = request.getRemoteAddr(); 
		Users userobj = userServices.getUserByEmail(username);
		IpLogs oldrec = ipLogServices.getLogsByAddrAndId(userip, userobj.getId());
		Date current = new Date();
		if (oldrec==null) {
			oldrec = new IpLogs();
			oldrec.setAdded(current);
			oldrec.setIpaddr(userip);
			oldrec.setLastused(current);
			oldrec.setTimesused(Long.valueOf(1));
			oldrec.setUserid(userobj.getId());
			ipLogServices.insertNewLog(oldrec);
		} else {
			oldrec.setLastused(current);
			oldrec.setTimesused(oldrec.getTimesused()+1);
			ipLogServices.updateLogInfo(oldrec);
		}
		userobj.setLastactive(current);
		userServices.updateUser(userobj);
	}
	
	public static Users setUserInfo(Subject currentUser,UserServices userServices,SubmissionServices submissionServices,ModelAndView mv) {
		if (currentUser==null) {
			return null;
		}
		if (currentUser.isRemembered()||currentUser.isAuthenticated()) {
			
			Users userobj = userServices.getUserByEmail((String)currentUser.getPrincipal());
			String NickName = userobj.getUsername();
			Long score = userobj.getScore();
			long rank = getUserrank(userobj,userServices,submissionServices);
			mv.addObject("nickname", NickName);
			mv.addObject("score", score);
			mv.addObject("rank", rank);
			return userobj;
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static long getUserrank(Users userobj,UserServices userServices,SubmissionServices submissionServices) {
		if (userobj.getRole().equals("admin")) {
			return 0;
		}
		List<Users> userforrank = userServices.getUsersForRank();
		ArrayList<RanklistObj> ranklist = new ArrayList<RanklistObj>();
		List<Submissions> allcorrect = submissionServices.getAllCorrectAndOrderByUserId();
		RanklistObj thisuser = null;
		for (Users u:userforrank) {
			RanklistObj aobj = new RanklistObj();
			Submissions last = null;//submissionServices.getLastCorrectSubmitByUserId(u.getId());
			for (Submissions sb:allcorrect) {
				if (sb.getUserid().longValue()==u.getId().longValue()) {
					last = sb;
					break;
				}
			}
			if (last==null) {
				aobj.setLastSummit(new Date());
			} else {
				aobj.setLastSummit(last.getSubmitTime());
			}
			aobj.setuserobj(u);
			ranklist.add(aobj);
			if (u.getId()-userobj.getId()==0) {
				thisuser = aobj;
			}
		}
		CompareScore c = new CompareScore();
		Collections.sort(ranklist,c);
		/*for (int i=0;i<ranklist.size()-1;i++) {
			for (int j=0;j<ranklist.size()-1;j++) {
				if (c.compare(ranklist.get(j),ranklist.get(j+1))==1) {
					RanklistObj tmp = ranklist.get(j);
					ranklist.set(j, ranklist.get(j+1));
					ranklist.set(j+1, tmp);
				}
			}
		}*/

//		for (RanklistObj robj:ranklist) {


//			System.out.println(robj.getuserobj().getScore()+" "+robj.getuserobj().getUsername()+" "+robj.getLastSummit());


//		}


		

		return ranklist.indexOf(thisuser)+1;
	}
	
	/**

	 * xss过滤器

	 * @param rawstr

	 * @return

	 */
	public static String XSSFilter(String rawstr) {
		String tmp = StringEscapeUtils.escapeHtml(rawstr);
		//tmp = StringEscapeUtils.escapeJavaScript(tmp);


		//tmp = StringEscapeUtils.escapeSql(tmp);


		return tmp;
	}
	
	public static boolean CheckIpBanned(HttpServletRequest request,BannedIpServices bannedIpServices) {
		String userip = request.getRemoteAddr();
		BannedIps banip = bannedIpServices.getRecordByIp(userip);
		if (banip!=null) {
			return true;
		} else {
			return false;
		}
		
	}
	
	public static String getFlagSalt() {
		return flag_salt;
	}
	
	public static boolean isValidDate(String str) {	
		boolean convertSuccess=true;
		// 指定日期格式为四位年/两位月份/两位日期，注意yyyy/MM/dd区分大小写；


		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		try {
			// 设置lenient为false. 否则SimpleDateFormat会比较宽松地验证日期，比如2007/02/29会被接受，并转换成2007/03/01


			format.setLenient(false);
			format.parse(str);
		} catch (ParseException e) {
			// e.printStackTrace();


			// 如果throw java.text.ParseException或者NullPointerException，就说明格式不对


			convertSuccess=false;
		} 
		return convertSuccess;
	}
	
}