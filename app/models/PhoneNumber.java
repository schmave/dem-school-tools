package models;

import java.util.*;

import javax.persistence.*;

import play.data.*;
import play.data.validation.Constraints.*;
import play.db.ebean.*;

@Entity
@Table(name="phone_numbers")
public class PhoneNumber extends Model {
    @Id
    public Integer id;

    public String number;
    public String comment;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="person_id")
    private Person owner;

    public static Finder<Integer, PhoneNumber> find = new Finder(
        Integer.class, PhoneNumber.class
    );

    public static PhoneNumber create(String number, String comment, Person owner) {
        PhoneNumber result = new PhoneNumber();
        result.number = number;
        result.comment = comment;
        result.owner = owner;

        result.save();
        return result;
    }
}
