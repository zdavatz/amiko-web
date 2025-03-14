export type Plain<T> = Pick<
    T,
    {
        [K in keyof T]: T[K] extends Function ? never : K;
    }[keyof T]
>;
export var PrescriptionLocalization: {
    prescription_please_choose_patient: string,
    prescription_confirm_clear: string,
    prescription_imported: string,
    export_to_zip: string,
    pdf_page_num: string,
    login_with_hin_adswiss: string,
    login_with_hin_sds: string,
    logout_from_hin_adswiss: string,
    logout_from_hin_sds: string,
    import_profile: string,
    sign_eprescription_confirm: string,
    send_to_zurrose: string,
    prescription_is_sent_to_zurrose: string,
    error: string,
} = (globalThis as any).PrescriptionLocalization;
