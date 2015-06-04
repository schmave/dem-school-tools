package models;

import java.io.IOException;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OrgConfig {
    public String name;
    public String short_name;
    public String people_url;
    public String str_manual_title;
    public String str_manual_title_short;
    public String str_res_plan_short;
    public String str_res_plan;
    public String str_res_plan_cap;
    public String str_res_plans;
    public String str_res_plans_cap;
    public String str_committee_name = "Judicial Committee";
	public String str_findings = "Findings";

    public boolean show_no_contest_plea = false;
    public boolean show_severity = false;
    public boolean use_minor_referrals = false;
    public boolean show_checkbox_for_res_plan = true;

    public Organization org;

    public static OrgConfig get() {
        Organization org = Organization.getByHost();
        OrgConfig result = configs.get(org.name);
        result.org = org;
        return result;
    }

    static HashMap<String, OrgConfig> configs = new HashMap<String, OrgConfig>();
    static void register(String name, OrgConfig config) {
        configs.put(name, config);
    }

    public String toJson() {
        ObjectMapper m = new ObjectMapper();
        try
        {
            return m.writeValueAsString(this);
        }
        catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
}

class ThreeRiversVillageSchool extends OrgConfig {
    private static final ThreeRiversVillageSchool INSTANCE = new ThreeRiversVillageSchool();

    public ThreeRiversVillageSchool() {
        name = "Three Rivers Village School";
        short_name = "TRVS";
        people_url = "http://trvs.demschooltools.com";

        str_manual_title = "Management Manual";
        str_manual_title_short = "Manual";
        str_res_plan_short = "RP";
        str_res_plan = "resolution plan";
        str_res_plan_cap = "Resolution plan";
        str_res_plans = "resolution plans";
        str_res_plans_cap = "Resolution plans";
        str_committee_name = "Justice Committee";

        show_checkbox_for_res_plan = false;

        OrgConfig.register(name, this);
    }

    public static ThreeRiversVillageSchool getInstance() {
        return INSTANCE;
    }
}

class PhillyFreeSchool extends OrgConfig {
    private static final PhillyFreeSchool INSTANCE = new PhillyFreeSchool();

    public PhillyFreeSchool() {
        name = "Philly Free School";
        short_name = "PFS";
        people_url = "http://pfs.demschooltools.com";

        str_manual_title = "Lawbook";
        str_manual_title_short = "Lawbook";
        str_res_plan_short = "sentence";
        str_res_plan = "sentence";
        str_res_plan_cap = "Sentence";
        str_res_plans = "sentences";
        str_res_plans_cap = "Sentences";

        show_no_contest_plea = true;
        show_severity = true;
        use_minor_referrals = true;

        OrgConfig.register(name, this);
    }

    public static PhillyFreeSchool getInstance() {
        return INSTANCE;
    }
}

class Fairhaven extends OrgConfig {
    private static final Fairhaven INSTANCE = new Fairhaven();

    public Fairhaven() {
        name = "Fairhaven School";
        short_name = "Fairhaven";
        people_url = "http://fairhaven.demschooltools.com";

        str_manual_title = "Lawbook";
        str_manual_title_short = "Lawbook";
        str_res_plan_short = "sentence";
        str_res_plan = "sentence";
        str_res_plan_cap = "Sentence";
        str_res_plans = "sentences";
        str_res_plans_cap = "Sentences";
		str_findings = "JC Report";

        use_minor_referrals = true;

        OrgConfig.register(name, this);
    }

    public static Fairhaven getInstance() {
        return INSTANCE;
    }
}
