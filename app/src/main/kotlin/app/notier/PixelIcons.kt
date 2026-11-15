package app.notier

internal fun pixelIcon(resource: Int) = when (resource) {
    R.drawable.ic_arrow_down_a_z -> R.drawable.px_arrow_down_a_z
    R.drawable.ic_arrow_down_up -> R.drawable.px_arrow_down_up
    R.drawable.ic_arrow_left -> R.drawable.px_arrow_left
    R.drawable.ic_arrow_up -> R.drawable.px_arrow_up
    R.drawable.ic_book_open -> R.drawable.px_book_open
    R.drawable.ic_bold -> R.drawable.px_bold
    R.drawable.ic_check -> R.drawable.px_check
    R.drawable.ic_chevron_down -> R.drawable.px_chevron_down
    R.drawable.ic_chevron_right -> R.drawable.px_chevron_right
    R.drawable.ic_circle_help -> R.drawable.px_circle_help
    R.drawable.ic_clock_3 -> R.drawable.px_clock_3
    R.drawable.ic_cloud -> R.drawable.px_cloud
    R.drawable.ic_cloud_download -> R.drawable.px_cloud_download
    R.drawable.ic_code -> R.drawable.px_code
    R.drawable.ic_ellipsis -> R.drawable.px_ellipsis
    R.drawable.ic_eye -> R.drawable.px_eye
    R.drawable.ic_file_code_2 -> R.drawable.px_file_code_2
    R.drawable.ic_file_down -> R.drawable.px_file_down
    R.drawable.ic_file_text -> R.drawable.px_file_text
    R.drawable.ic_folder_open -> R.drawable.px_folder_open
    R.drawable.ic_folder_plus -> R.drawable.px_folder_plus
    R.drawable.ic_hard_drive -> R.drawable.px_hard_drive
    R.drawable.ic_image -> R.drawable.px_image
    R.drawable.ic_italic_letter -> R.drawable.px_italic_letter
    R.drawable.ic_languages -> R.drawable.px_languages
    R.drawable.ic_link_2 -> R.drawable.px_link_2
    R.drawable.ic_list -> R.drawable.px_list
    R.drawable.ic_list_ordered -> R.drawable.px_list_ordered
    R.drawable.ic_list_todo -> R.drawable.px_list_todo
    R.drawable.ic_log_in -> R.drawable.px_log_in
    R.drawable.ic_log_out -> R.drawable.px_log_out
    R.drawable.ic_minus -> R.drawable.px_minus
    R.drawable.ic_pencil -> R.drawable.px_pencil
    R.drawable.ic_pin -> R.drawable.px_pin
    R.drawable.ic_pin_off -> R.drawable.px_pin_off
    R.drawable.ic_plus -> R.drawable.px_plus
    R.drawable.ic_quote -> R.drawable.px_quote
    R.drawable.ic_redo_2 -> R.drawable.px_redo_2
    R.drawable.ic_refresh_cw -> R.drawable.px_refresh_cw
    R.drawable.ic_rotate_ccw -> R.drawable.px_rotate_ccw
    R.drawable.ic_save -> R.drawable.px_save
    R.drawable.ic_scaling -> R.drawable.px_scaling
    R.drawable.ic_search -> R.drawable.px_search
    R.drawable.ic_settings -> R.drawable.px_settings
    R.drawable.ic_share_2 -> R.drawable.px_share_2
    R.drawable.ic_square_mouse_pointer -> R.drawable.px_square_mouse_pointer
    R.drawable.ic_strikethrough -> R.drawable.px_strikethrough
    R.drawable.ic_sun_moon -> R.drawable.px_sun_moon
    R.drawable.ic_text_cursor_input -> R.drawable.px_text_cursor_input
    R.drawable.ic_trash_2 -> R.drawable.px_trash_2
    R.drawable.ic_type -> R.drawable.px_type
    R.drawable.ic_undo_2 -> R.drawable.px_undo_2
    R.drawable.ic_x -> R.drawable.px_x
    else -> resource
}

internal fun usesPixelColors(resource: Int) = when (resource) {
    R.drawable.ic_book_open, R.drawable.ic_cloud, R.drawable.ic_cloud_download, R.drawable.ic_eye,
    R.drawable.ic_file_text, R.drawable.ic_folder_open, R.drawable.ic_hard_drive, R.drawable.ic_list,
    R.drawable.ic_list_ordered, R.drawable.ic_pencil, R.drawable.ic_plus, R.drawable.ic_save,
    R.drawable.ic_search, R.drawable.ic_settings, R.drawable.ic_x -> true
    else -> false
}
