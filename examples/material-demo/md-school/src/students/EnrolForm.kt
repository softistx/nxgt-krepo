package com.softistx.material.school.students

import com.softistx.material.components.form.state.FormState
import com.softistx.material.components.form.state.chosen
import com.softistx.material.components.form.state.email
import com.softistx.material.components.form.state.minLength
import com.softistx.material.components.form.state.required
import io.konform.validation.Validation

/** What the enrolment wizard assembles. One value, three steps. */
data class Enrolment(
    val firstName: String = "",
    val lastName: String = "",
    val className: String? = null,
    val guardianName: String = "",
    val guardianEmail: String = "",
)

/** The fields each step owns, so a step can ask whether it may be left. */
val EnrolSteps: List<List<kotlin.reflect.KProperty1<Enrolment, *>>> =
    listOf(
        listOf(Enrolment::firstName, Enrolment::lastName),
        listOf(Enrolment::className),
        listOf(Enrolment::guardianName, Enrolment::guardianEmail),
    )

class EnrolForm :
    FormState<Enrolment>(Enrolment(), EnrolSteps.flatten()) {
    override val validate: Validation<Enrolment> =
        Validation {
            Enrolment::firstName {
                required()
                minLength(2)
            }
            Enrolment::lastName { required() }
            Enrolment::className { chosen() }
            Enrolment::guardianName { required() }
            Enrolment::guardianEmail {
                required()
                email()
            }
        }

    /** True when every field of [step] already passes, without publishing any complaint. */
    fun stepValid(step: Int): Boolean {
        val result = validate(value)
        return EnrolSteps[step].all { path -> result.errors.none { it.dataPath.contains(path.name) } }
    }
}
