package com.softistx.material.demo.stories.components.form

import com.softistx.material.demo.storyGroup

val FormStories =
    storyGroup("Forms") {
        textInputStories()
        choiceInputStories()
        fieldStories()
        formLayoutStories()
    }
